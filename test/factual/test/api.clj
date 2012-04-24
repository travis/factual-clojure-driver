(ns factual.test.api
  (:require [factual.api :as api])
  (:import [factual.api factual-error])
  (:use [clojure.test]
        [clojure.data.json :only (json-str read-json)]
        [slingshot.slingshot]))

(defn connect
  "Test fixture that connects this namespace to Factual's API.
   You must put your key and secret in resources/oauth.json.
   See resources/oauth.sample.json for the expected format."
  [f]
  (let [auth (read-json (slurp "resources/oauth.json"))]
    (api/factual! (:key auth) (:secret auth)))
  (f))

(use-fixtures :once connect)

(deftest test-fetch-random-sample
  (is (< 10 (count (api/fetch {:table :places})))))

(deftest test-fetch-filters
  (let [res (api/fetch {:table :restaurants-us :filters {:name "Starbucks"}})
        uniq-names (vec (distinct (map :name res)))]
    (is (= ["Starbucks"] uniq-names))))

(deftest test-fetch-factual-error
  (try+
    (api/fetch {:table :places
                :filters {:factual_id "97598010-433f-4946-8fd5-4a6dd1639d77" :BAD :PARAM}})
    (catch factual-error {code :code message :message opts :opts}
     (is (not (nil? code)))
     (is (not (nil? message)))
     (is (not (nil? opts)))
     (is (= (get-in opts [:filters :BAD]) :PARAM)))))

(deftest test-fetch-nearby-cafes
  "Returns up to 50 cafes within specified miles of specified location."
  []
  (let [res (api/fetch {:table :places
              :q "cafe"
              :filters {:category {:$eq "Food & Beverage"}}
              :geo {:$circle {:$center [34.06018 -118.41835]
                              :$meters (* 3 1609.344)}}
              :include_count true
                        :limit 5})]
    (is (= 5 (count res)))
    (is (< 5 (get-in (meta res) [:response :total_row_count])))))

(deftest test-resolve
  (let [res (first
             (api/resolve {:name "taco" :region "CA" :locality "los angeles"
                           :address "10250 santa monica"}))]
    (is (= true (:resolved res)))))

(deftest test-crosswalk
  (is (< 3 (count
             (api/crosswalk :factual_id "97598010-433f-4946-8fd5-4a6dd1639d77")))))