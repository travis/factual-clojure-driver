(ns clj-factual.api
  (:refer-clojure :exclude [resolve])
  (:import (com.google.api.client.auth.oauth OAuthHmacSigner OAuthParameters))
  (:import (com.google.api.client.http.javanet NetHttpTransport))
  (:use [clojure.data.json :only (json-str read-json)])
  (:use [clojure.java.io :only (reader)])
  (:use [slingshot.slingshot :only [throw+]])
  (:import (com.google.api.client.http UrlEncodedContent GenericUrl HttpResponseException HttpHeaders)))

;; TODO: Factor out oauth/request solution into its own lib?

(def DRIVER_VERSION_TAG "factual-clojure-driver-v1.3.1")

(declare ^:dynamic *factual-config*)

(defrecord factual-error [code message opts])

(def ^:dynamic *base-url* "http://api.v3.factual.com/")

(def ^:dynamic *debug* false)

(defn factual!
  [key secret]
  (def ^:dynamic *factual-config* {:key key :secret secret}))

(defn make-params
  "Returns configured OAuth params for the specified request.
   gurl must be a GenericUrl."
  [gurl method]
  (let [signer (OAuthHmacSigner.)
        params (OAuthParameters.)]
   (set! (. params consumerKey) (:key *factual-config*))
    (doto params
      (.computeNonce)
      (.computeTimestamp))
    (set! (. signer clientSharedSecret) (:secret *factual-config*))
    (set! (. params signer) signer)
    (.computeSignature params method gurl)
    params))

(defn make-req
  "gurl must be a GenericUrl."
  [gurl]
  (let [params (make-params gurl "GET")
        factory (.createRequestFactory (NetHttpTransport.) params)
        req (.buildGetRequest factory gurl)
        heads (HttpHeaders.)]
    (.set heads "X-Factual-Lib" DRIVER_VERSION_TAG)
    (.setHeaders req heads)
    req))

(defn get-resp
  "Takes a GenericUrl, executes it, and returns the
   resulting HttpResponse."
  [gurl]
  (.execute (make-req gurl)))

(defn make-gurl-map
  "Builds a GenericUrl pointing to the given path on Factual's API,
   and including opts as key value parameters in the query string.

   opts should be a hashmap with all desired query parameters for
   the resulting url. Values in opts should be primitives or hash-maps;
   they will be coerced to the proper json string representation for
   inclusion in the url query string.

   Returns a hash-map that holds the GenericUrl (as :gurl), as well as
   the original opts (as :opts). This is useful later for error
   handling, in order to include opts in the thrown error."
  [path opts]
  (let [gurl (GenericUrl. (str *base-url* path))]
    (doseq [[k v] opts]
      (.put gurl
        ;; query param name    
        (name k)
        ;; query param value
        (if (or (keyword? v) (string? v))
          (name v)
          (json-str v))))
    {:gurl gurl :opts opts}))

(defn do-meta [res]
  (let [data (or
               ;; standard result set
               (get-in res [:response :data])
               ;; schema result
               (get-in res [:response :view :fields]))]
    (with-meta data (merge
                     (dissoc res :response)
                     {:response (dissoc (:response res) :data)}))))

(defn new-error
  "Given an HttpResponseException, returns a factual-error record representing
   the error response, which includes things like status code, status message, as
   well as the original opts used to create the request."
  [hre gurl-map]
  (let [res (.getResponse hre)
        code (.getStatusCode res)
        msg (.getStatusMessage res)
        opts (:opts gurl-map)]
    (factual-error. code msg opts)))

(defn get-content [resp]
  (slurp (reader (.getContent resp))))

(defn debug-resp [resp content]
  (println "--- clj-factual debug ---")
  (println "req url:" (.build (. (.request resp) url)))
  (let [hdrs (into {} (.canonicalMap (.getHeaders resp)))]
    (println "resp headers:")
    (clojure.pprint/pprint hdrs))
  (println "resp status code:" (. resp statusCode))
  (println "resp status message:" (. resp statusMessage))
  (println "resp body:")
  (println content))

(defn get-results
  "Executes the specified query and returns the results.
   The returned results will have metadata associated with it,
   built from the results metadata returned by Factual.

   In the case of a bad response code, throws a factual-error record
   as a slingshot stone. The record will include any opts that were
   passed in by user code."
  ([gurl-map]
     (try
       (let [resp (get-resp (:gurl gurl-map))
             content (get-content resp)]
         (when *debug* (debug-resp resp content))
         (do-meta (read-json content)))
       (catch RuntimeException re
         ;; would be nice if HttpResponseException was at the top
         ;; level, however seems like it comes back nested at least
         ;; some of the time
         (if (= HttpResponseException (class (.getCause re)))
           (throw+ (new-error (.getCause re) gurl-map))
           (throw re)))
       (catch HttpResponseException hre
         (throw+ (new-error hre gurl-map)))))
  ([path opts]
     (get-results (make-gurl-map path opts))))

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
  (get-results (str "t/" (name (:table q))) (dissoc q :table)))

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
  (get-results (str "t/" (name (:table q)) "/facets") (dissoc q :table)))

(defn schema
  "Returns the schema of the specified table, as a hash-map. Example usage:
   (schema :places)"
  [table]
  (get-results (str "t/" (name table) "/schema") []))

(defn crosswalk [& {:as opts}]
  (map #(update-in % [:namespace] keyword)
       (get-results "places/crosswalk" opts)))

(defn resolve [values]
  (get-results "places/resolve" {:values values}))

(defn resolved [values]
  (first (filter :resolved
                 (get-results "places/resolve" {:values values}))))

(defmacro with-debug
  [& body]
  `(binding [*debug* true]
     ~@body))
