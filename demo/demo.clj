(ns factual.demo
  (:require [factual.api :as fact]
            [clojure.data.json :as json]))

(defn connect
  "Connects this demo namespace to Factual's API. You must put
   your key and secret in resources/oauth.json.
   See resources/oauth.sample.json for the expected format."
  []
  (let [{:keys [key secret]} (json/read-json (slurp "resources/oauth.json"))]
    (fact/factual! key secret)))

(defn simple-fetch
  "Returns 3 random records from Factual's Places dataset"
  []
  (fact/fetch :places {:limit 3}))


(defmulti myf (fn [& args]
                 (if (= 1 (count args))
                   (if (map? (first args))
                     :q :table)
                   :table-q)))
                                      
(defmethod myf :q [m]
  (println "Q VERSION"))
(defmethod myf :table [m]
  (println "TABLE VERSION"))
(defmethod myf :table-q [table q]
  (println "TABLE AND Q VERSION"))

