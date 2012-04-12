(ns clj-factual.http
  (:import (com.google.api.client.http UrlEncodedContent GenericUrl HttpHeaders))
  (:import (com.google.api.client.auth.oauth OAuthHmacSigner OAuthParameters))
  (:import (com.google.api.client.http.javanet NetHttpTransport))
  (:use [clojure.data.json :only (json-str)]))

(defn oauth-params
  "Returns configured OAuth params for the specified request.
   gurl must be a GenericUrl.
   auth must be a hash-map like so: {:key FACTUAL_API_KEY :secret FACTUAL_API_SECRET}

   method should be :get or :post"
  [gurl method auth]
  (let [signer (OAuthHmacSigner.)
        params (OAuthParameters.)
        method (method {:get "GET" :post "POST"})]
   (set! (. params consumerKey) (:key auth))
    (doto params
      (.computeNonce)
      (.computeTimestamp))
    (set! (. signer clientSharedSecret) (:secret auth))
    (set! (. params signer) signer)
    (.computeSignature params method gurl)
    params))

(defn json-params [m]
  (reduce
   (fn [m [k v]]
     (assoc m
       ;; query param name
       (name k)
       ;; query param value
       (if (or (keyword? v) (string? v))
            (name v)
            (json-str v))))
   {} m))

(defn make-gurl
  "Builds a GenericUrl pointing to the given url, including params as
   key value parameters in the query string.

   Returns a GenericUrl built from url and params."
  [url params]
  (let [gurl (GenericUrl. url)]
    (doseq [[k v] params]
      (.put gurl k v))
    gurl))
    
(defn request
  "Runs the specified request and returns the resulting HttpResponse.

   The passed in hash-map should specify the full request to make.

   Query string parameters are specified with :params. The value associated
   with :params should be a hashmap with all desired query parameters for
   the resulting url. Values should be primitives, vectors, or hash-maps;
   they will be coerced to the proper json string representation for
   inclusion in the url query string."
  [{:keys [method url params content headers auth]}]
  (let [gurl (make-gurl url (json-params params))
        factory (.createRequestFactory (NetHttpTransport.) (oauth-params gurl method auth))
        req (if (= :post method)
              (.buildPostRequest factory gurl (UrlEncodedContent. (json-params content)))
              (.buildGetRequest factory gurl))]
    (when headers (.setHeaders req (doto (HttpHeaders.) (.putAll headers))))
    (.execute req)))

