(ns factual.api
  (:refer-clojure :exclude [resolve])
  (:require [factual.http :as http])
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
  "Sets your authentication with the Factual service.
   key is your Factual API key
   secret is your Factual API secret"
  [key secret]
  (def ^:dynamic *factual-config* {:key key :secret secret}))

(defmacro service!
  "Sets this driver to use the specified base service URL.
   This could be handy for testing purposes, or if Factual is
   supporting a custom service for you. Example usage:
     (service! \"http://api.dev.cloud.factual\")

   Don't forget the leading http:// and don't forget the trailing /
   in the base url you supply."
  [base]
  (def ^:dynamic *base-url* base))

(defmacro with-service
  "Allows temporary use of a different base service URL, within the
   scope of body. This could be handy for testing purposes, or if
   Factual is supporting a custom service for you. Example usage:
     (with-service \"http://api.dev.cloud.factual.com/\"
       (fetch :places))

   Don't forget the leading http:// and don't forget the trailing /
   in the base url you supply."
  [base & body]
  `(binding [*base-url* ~base] ~@body))

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
     (time ~@body)))

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
  (println "--- response debug ---")
  (let [req (.getRequest resp)
        hdrs (into {} (.getHeaders resp))]
    (println "resp status code:" (.getStatusCode resp))
    (println "resp status message:" (.getStatusMessage resp))
    (println "resp headers:")
    (clojure.pprint/pprint (into {} hdrs)))
  (println "resp body:" body)
  (println body)
  (println "----------------------"))

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
          resp (http/request {:method method :url url :headers headers :params params :content content :auth *factual-config* :debug *debug*})
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

(defn fetch-disp
  "Dispatch method for fetch. Returns:
   :q if argument is a query map
   :table if argument is just the table name
   :table-and-q if 2 arguments, expected to be table name and query map"
  [& args]
  (if (= 1 (count args))
    (if (map? (first args))
      :q :table)
      :table-and-q))

(defmulti fetch
  "Runs a fetch request against Factual and returns the results.

   Supports 3 variations depending on the arguments you pass in:

   Variation 1: [q]
   q is a hash-map specifying the full query. The only required
   entry is :table, which must be associated with valid Factual
   table name. Optional query parameters, such as row filters and geolocation
   queries, are specified with further entries in q. Example usages:
     (fetch {:table :global})
     (fetch {:table :places :q \"cafe\"})

  Variation 2: [table q]
  table is the name of a valid Factual table.
  q is a hash-map specifying the full query, such as row filters and geolocation
  queries. Example usage:
    (fetch :places {:q \"cafe\" :limit 10})

  Variation 3: [table]
  table is the name of a valid Factual table. This is a very limited call, since
  it does not support any query parameters and will therefore just return a
  random sample of results from the specified table. Example usage:
    (fetch :places)"
  fetch-disp)

(defmethod fetch :q
  [q]
  {:pre [(:table q)]}
  (get-results {:path (str "t/" (name (:table q))) :params (dissoc q :table)}))

(defmethod fetch :table
  [table]
  (fetch {:table table}))

(defmethod fetch :table-and-q
  [table q]
  (fetch (assoc q :table table)))

(defn facets-disp
  "Dispatch method for facets. Returns:
   :q if argument is a query map
   :table-and-select if table name and select(s) are specified
   :table-and-q if table name and query options are specified"
  [& args]
  (if (= 1 (count args))
    :q
    (if (map? (second args))
      :table-and-q
      :table-and-select)))

(defmulti facets
  "Runs a Facets request against Factual and returns the results.

   Supports 3 variations depending on the arguments you pass in:

   Variation 1: [q]
   q is a hash-map specifying the full query, which can include things like row filters and
   geolocation filtering. Required entries:
     :table   the name of a valid Factual table, e.g. :places
     :select  the field(s) to Facet as a comma-delimted string, e.g. \"locality,region\"
   Example usages:
     (facets {:table :global :select \"locality\"})
     (facets {:table :places :select \"locality,region\" :q \"starbucks\"})

   Variation 2: [table q]
   table is the name of a valid Factual table.
   q is a hash-map specifying the full query, which can include things like row filters and
   geolocation filtering. Required entry:
     :select  the field(s) to Facet as a comma-delimted string, e.g. \"locality,region\"
   Example usages:
     (facets :restaurants-us {:select \"region\" :limit 50})
     (facets :global {:select \"locality,region\" :q \"starbucks\"})

   Variation 3: [table select]
   table is the name of a valid Factual table.
   select is the field(s) to Facet as a comma-delimted string, e.g. \"locality,region\"
   This is a somewhat limited variation, since you can't specify any additional query options. But
   it's useful if you want overall counts, e.g. 'how many U.S. restaurants are there in each locality?':
     (facets :us-restaurants \"locality\")"
  facets-disp)

(defmethod facets :q
  [q]
  {:pre [(:table q)(:select q)]}
  (get-results {:path (str "t/" (name (:table q)) "/facets") :params (dissoc q :table)}))

(defmethod facets :table-and-q
  [table q]
  {:pre [(:select q)]}
  (facets (assoc q :table table)))

(defmethod facets :table-and-select
  [table select]
  (facets {:table table :select select}))

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

(defn geopulse
  "Runs a Geopulse request against Factual and returns the results.

   q is a hash-map specifying the Geopulse query. It must contain :geo.
   It can optionally contain :select. If :select is included, it must be a
   comma delimited list of available Factual pulses, such as \"income\",
   \"race\", \"age_by_gender\", etc.

   Example usage:
   (geopulse {:geo {:$point [34.06021,-118.41828]}})

   Example usage:
   (geopulse {:geo {:$point [34.06021,-118.41828]} :select \"income,race,age_by_gender\"})"
  [q]
  (get-results {:path "places/geopulse" :params q}))

(defn reverse-geocode
  "Given latitude lat and longitude lon, uses Factual's reverse geocoder to return the
   nearest valid address.

   Example usage:
   (reverse-geocode 34.06021,-118.41828)"
  [lat lon]
  (get-results {:path "places/geocode" :params {:geo { :$point [lat lon]}}}))
