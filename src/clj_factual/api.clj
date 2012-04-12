(ns clj-factual.api
  (:refer-clojure :exclude [resolve])
  (:require [clj-factual.http :as http])
  (:use [clojure.data.json :only (read-json)])
  (:use [slingshot.slingshot :only [throw+]]
        [clojure.java.io :only (reader)])
  (:import (com.google.api.client.http HttpResponseException)))

(def DRIVER_VERSION_TAG "factual-clojure-driver-v1.3.1")

(declare ^:dynamic *factual-config*)
(defrecord factual-error [code message opts])

(def ^:dynamic *base-url* "http://api.v3.factual.com/")

(def ^:dynamic *debug* false)

(defn factual!
  [key secret]
  (def ^:dynamic *factual-config* {:key key :secret secret}))

(defn do-meta [res]
  (let [data (or
               ;; standard result set
               (get-in res [:response :data])
               ;; schema result
               (get-in res [:response :view :fields]))]
    (with-meta data (merge
                     (dissoc res :response)
                     {:response (dissoc (:response res) :data)}))))

(defmacro with-debug
  [& body]
  `(binding [*debug* true]
     ~@body))

(defn new-error
  "Given an HttpResponseException, returns a factual-error record representing
   the error response, which includes things like status code, status message, as
   well as the original opts used to create the request."
  [hre opts]
  (let [res (.getResponse hre)
        code (.getStatusCode res)
        msg (.getStatusMessage res)]
    (factual-error. code msg opts)))

(defn debug-resp [resp body]
  (println "--- clj-factual debug ---")
  (let [req (.getRequest resp)
        gurl (.getUrl req)
        hdrs (into {} (.getHeaders resp))]
    (println "req url:" (.build gurl))
    (println "resp status code:" (.getStatusCode resp))
    (println "resp status message:" (.getStatusMessage resp))    
    (println "resp headers:")
    (clojure.pprint/pprint hdrs))
  (println "resp body:")
  (println body))

(defn get-results
  "Executes the specified request and returns the results.

   The incoming argument must be a map representing the request.

   The returned results will have metadata associated with it,
   built from the results metadata returned by Factual.

   In the case of a bad response code, throws a factual-error record
   as a slingshot stone. The record will include any opts that were
   passed in by user code."
   [{:keys [method path params content] :or {method :get}}]
     (try
       (let [url (str *base-url* path)
             headers {"X-Factual-Lib" DRIVER_VERSION_TAG}
             resp (http/request {:method method :url url :headers headers :params params :content content :auth *factual-config*})
             body (slurp (reader (.getContent resp)))]
         (when *debug* (debug-resp resp body))
         (do-meta (read-json body)))
       (catch RuntimeException re
         ;; would be nice if HttpResponseException was at the top
         ;; level, however seems like it comes back nested at least
         ;; some of the time
         (if (= HttpResponseException (class (.getCause re)))
           (throw+ (new-error (.getCause re) params))
           (throw re)))
       (catch HttpResponseException hre
         (throw+ (new-error hre params)))))

(defn fetch
  "Runs a fetch request against Factual and returns the results.
   q is a hash-map specifying the full query. The only required
   entry is :table, which must be associated with valid Factual
   table name.

   Optional query parameters, such as row filters and geolocation
   queries, are specified with further entries in q.

   Example usages:

   (fetch {:table :global})

   (fetch {table :places :q \"cafe\"})

   (fetch
     {:table  :restaurants-us
      :q \"cafe\"
      :offset 20
      :limit 10
      :filters {:name {:$bw \"starbucks\" :locality {:$eq \"los angeles\"}}}}))"
  [q]
  {:pre [(:table q)]}
  (get-results {:path (str "t/" (name (:table q))) :params (dissoc q :table)}))

(defn facets
  "Runs a Facets request against Factual and returns the results.

   q is a hash-map specifying the full query. The required entries
   are:

     :table  the name of a valid Factual table, e.g. :places
     :select the field(s) to Facet. comma-delimted string, e.g. \"locality,region\"

   Optional query parameters, such as row filters and geolocation
   queries, are specified with further entries in q.

   Facets give you row counts for Factual tables, grouped by facets of the data.
   For example, you may want to query all businesses within 1 mile of a location
   and for a count of those businesses by category:

   (facets {:table :global :select \"category\"
            :geo {:$circle {:$center [34.06018, -118.41835] :$meters 5000}}})"
  [q]
  {:pre [(:table q)(:select q)]}
  (get-results {:path (str "t/" (name (:table q)) "/facets") :params (dissoc q :table)}))

(defn schema
  "Returns the schema of the specified table, as a hash-map. Example usage:
   (schema :places)"
  [table]
  (get-results {:path (str "t/" (name table) "/schema")}))

(defn crosswalk [& {:as opts}]
  (map #(update-in % [:namespace] keyword)
       (get-results {:path "places/crosswalk" :params opts})))

(defn resolve [values]
  (get-results {:path "places/resolve" :params {:values values}}))

(defn resolved [values]
  (first (filter :resolved
                 (get-results {:path "places/resolve" :params {:values values}}))))

(defn contribute [c]
  {:pre [(:table c)(:values c) (:user c)]}
  (let [path (str "t/" (name (:table c)) "/contribute")
        params {:user (:user c)}]
    (get-results {:path path :method :post :params params :content (:values c)})))

(defn flag
  "Flags a specified entity.

   f must be a hash-map containing:
     :table :id :problem :user
   f may optionally contain
     :comment :reference

   :problem must be one of:
     :duplicate, :inaccurate, :inappropriate, :nonexistent, :spam, :other"
  [f]
  {:pre [(:table f)(:id f)(:problem f) (:user f)]}
  (let [path (str "t/" (name (:table f)) "/" (name (:id f)) "/flag")
        content (select-keys f [:problem :user :comment :reference])]
    (get-results {:path path :method :post :content content})))