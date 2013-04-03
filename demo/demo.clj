(ns factual.demo
  (:require [factual.api :as fact]
            [sosueme.conf :as conf]))

(defn connect
  "Connects this demo namespace to Factual's API. You must put
   your key and secret in resources/oauth.json.
   See resources/oauth.sample.json for the expected format."
  []
  (let [{:keys [key secret]} (conf/dot-factual "factual-auth.yaml")]
    (fact/factual! key secret)))

(defn simple-fetch
  "Returns 3 random records from Factual's Places dataset"
  []
  (fact/fetch :places {:limit 3}))
