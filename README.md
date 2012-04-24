# About

factual-clojure-driver is a Clojure driver for Factual's API. It supports rich queries across Factual's datasets, including support for the Crosswalk and Resolve services.

Factual's [web-based API](http://developer.factual.com) offers:

* Rich queries across curated datasets U.S. points of interest, global points of interest, and U.S. restaurants with long tail attributes.
* Crosswalk: Translation between Factual IDs, third party IDs, and URLs that represent the same entity across the internet.
* Resolve: An entity resolution API that makes partial records complete, matches one entity against another, and assists in de-duping and normalizing datasets.

# Installation

The driver is hosted at [Clojars](http://clojars.org/factual-clojure-driver). Just add this to your dependencies:

	[factual-clojure-driver "1.3.1"]

# Basics

## Setup

````clojure
(ns yournamespace.core
  (:require [factual.api :as facts]))
  (facts/factual! "YOUR_FACTUAL_KEY" "YOUR_FACTUAL_SECRET")
````

## Simple Fetch

````clojure
;; Fetch 3 random Places from Factual
(facts/fetch {:table :places :limit 3})
````

<tt>fetch</tt> takes a hash-map as its argument, which specifies the full query. The only required entry is :table, which must be associated with valid Factual table name. Optional query parameters, such as row filters and geolocation, are specified with further entries in q.

<tt>fetch</tt> returns a sequence of records, where each record is a hashmap representing a row of results. So our results from above will look like:

````clojure
[{:status 1, :country US, :longitude -94.819339, :name Lorillard Tobacco Co., :postcode 66218, ... }
 {:status 1, :country US, :longitude -118.300024, :name Imediahouse, :postcode 90005, ... }
 {:status 1, :country US, :longitude -118.03132, :name El Monte Wholesale Meats, :postcode 91733, ... }]
````

More examples:

````clojure
;; Sample three business names from the Places dataset (U.S. points of interest):
> (map :name (facts/fetch {:table :places :limit 3}))
("Lorillard Tobacco Co." "Imediahouse" "El Monte Wholesale Meats")
````

````clojure
;; Return rows with a name equal to "Stand" within 5000 meters of the specified lat/lng
(facts/fetch {:table :places
	      :filters {:name "Stand"}
	      :geo {:$circle {:$center [34.06018, -118.41835] :$meters 5000}}})
````

````clojure
;; A function that finds restaurants near a given latitude/longitude that deliver dinner, sorted by distance:
(defn deliver-dinner [lat lon]
  (facts/fetch {:table :restaurants-us
	        :filters {:meal_dinner {:$eq true}
	                  :meal_deliver {:$eq true}}
	        :geo {:$circle {:$center [lat lon]
	                        :$meters 4500}}
	        :sort :$distance}))
````

## Using Fetch with any Factual dataset

<tt>fetch</tt> allows you to specify any valid Factual dataset. E.g.:

````clojure
(facts/fetch {:table :places :limit 3})
(facts/fetch {:table :restaurants-us :limit 3})
(facts/fetch {:table :global :limit 3})
````

## More Fetch Examples

````clojure
;; Return rows where region equals "CA"
(facts/fetch {:table :places :filters {"region" "CA"}})
````

````clojure
;; Return rows where name begins with "Starbucks" and return both the data and a total count of the matched rows:
(facts/fetch :places :filters {:name {:$bw "Starbucks"}} :include_count true)
````

````clojure
;; Do a full text search for rows that contain "Starbucks" or "Santa Monica"
(facts/fetch {:table :places :q "Starbucks,Santa Monica"})
````

````clojure
;; Do a full text search for rows that contain "Starbucks" or "Santa Monica" and return rows 20-40
(facts/fetch {:table :places :q "Starbucks,Santa Monica" :offset 20 :limit 20})
````

````clojure
;; Count all businesses in Chiang Mai, Thailand that are operational and have a telephone number
(get-in
  (meta
	  (facts/fetch {:table :global
       	               	:include_count true
	            	:filters {:country {:$eq "TH"}
			          :region {:$eq "Chiang Mai"}
                                  :status {:$eq 1}
                                  :tel {:$blank false}}}))
	  [:response :total_row_count])
````

# Row Filters

The driver supports all available row filtering logic. Examples:

````clojure
;;; Fetch places whose name field starts with "Starbucks"
(facts/fetch {:table :places :filters {:name {:$bw "Starbucks"}}})
````

````clojure
;;; Fetch U.S. restaurants that have a blank telephone number
(facts/fetch {:table :restaurants-us :filters {:tel {:$blank true}}})
````

````clojure
;;; Fetch U.S. restaurants from one of five states
(facts/fetch {:table :restaurants-us
              :filters {:region {:$in ["MA", "VT", "NH", "RI", "CT"]}}})
````

## Supported row filter logic

<table>
  <tr>
    <th>Predicate</th>
    <th>Description</th>
    <th>Example :filters</th>
  </tr>
  <tr>
    <td>$eq</td>
    <td>equal to</td>
    <td><tt>{:region {:$eq "CA"}}</tt></td>
  </tr>
  <tr>
    <td>$neq</td>
    <td>not equal to</td>
    <td><tt>{:region {:$neq "CA"}}</tt></td>
  </tr>
  <tr>
    <td>$search</td>
    <td>full text search</td>
    <td><tt>{:name {:$search "fried chicken"}}</tt></td>
  </tr>
  <tr>
    <td>$in</td>
    <td>equals any of</td>
    <td><tt>{:region {:$in ["MA", "VT", "NH", "RI", "CT"]}}</tt></td>
  </tr>
  <tr>
    <td>$nin</td>
    <td>does not equal any of</td>
    <td><tt>{:region {:$nin ["MA", "VT", "NH", "RI", "CT"]}}</tt></td>
  </tr>
  <tr>
    <td>$bw</td>
    <td>begins with</td>
    <td><tt>{:name {:$bw "starbucks"}}</tt></td>
  </tr>
  <tr>
    <td>$nbw</td>
    <td>does not begin with</td>
    <td><tt>{:name {:$nbw "starbucks"}}</tt></td>
  </tr>
  <tr>
    <td>$bwin</td>
    <td>begins with any of</td>
    <td><tt>{:name {:$bwin ["starbucks" "tea" "coffee"]}}</tt></td>
  </tr>
  <tr>
    <td>$nbwin</td>
    <td>does not begin with any of</td>
    <td><tt>{:name {:$nbwin ["starbucks" "tea" "coffee"]}}</tt></td>
  </tr>
  <tr>
    <td>$blank</td>
    <td>whether is blank or null</td>
    <td><tt>{:name {:$blank true}}<br>
            {:name {:$blank false}}</tt></td>
  </tr>
  <tr>
    <td>$gt</td>
    <td>greater than</td>
    <td><tt>{:rating {:$gt 3.0}}</tt></td>
  </tr>
  <tr>
    <td>$gte</td>
    <td>greater than or equal to</td>
    <td><tt>{:rating {:$gte 3.0}}</tt></td>
  </tr>
  <tr>
    <td>$lt</td>
    <td>less than</td>
    <td><tt>{:rating {:$lt 3.0}}</tt></td>
  </tr>
  <tr>
    <td>$lte</td>
    <td>less than or equal to</td>
    <td><tt>{:rating {:$lte 3.0}}</tt></td>
  </tr>
</table>

# Facets Usage

````clojure
;; Count Starbucks in the US by city and state
(facts/facets {:table :global :select "locality,region" :q "starbucks" :filters {:country :US}})
````

````clojure
;; Count businesses by category 5 km around a lat, lon:
(facts/facets {:table :global :select "category" :geo {:$circle {:$center [34.06018, -118.41835] :$meters 5000}}})
````

# Crosswalk Usage

````clojure
;; Return all Crosswalk data for the place identified by the specified Factual ID
(facts/crosswalk :factual_id "97598010-433f-4946-8fd5-4a6dd1639d77")
````

````clojure
;; Return Loopt.com Crosswalk data for the place identified by the specified Factual ID
(facts/crosswalk :factual_id "97598010-433f-4946-8fd5-4a6dd1639d77" :only "loopt")
````

````clojure
;; Return all Crosswalk data for the place identified by the specified Foursquare ID
(facts/crosswalk :namespace "foursquare" :namespace_id "4ae4df6df964a520019f21e3")
````

````clojure
;; Return the Yelp.com Crosswalk data for the place identified by a Foursquare ID: 
(facts/crosswalk :namespace "foursquare" :namespace_id "4ae4df6df964a520019f21e3" :only "yelp")
````

# Resolve Usage

The <tt>resolve</tt> function takes a hash-map of values indicating what you know about a place. It returns the set of potential matches, including a similarity score.

	> (facts/resolve {:name "ino", :latitude 40.73, :longitude -74.01}))
	
The <tt>resolved</tt> function takes a hash-map of values indicating what you know about a place. It returns either a certain match, or nil.

	> (facts/resolved {:name "ino", :latitude 40.73, :longitude -74.01}))

# Results Metadata

Factual's API returns more than just results rows. It also returns various metadata about the results. You can access this metadata by using Clojure's <tt>meta</tt> function on your results. Examples:

````clojure
> (meta (facts/fetch {:table :places :filters {:name {:$bw "Starbucks"}} :include_count true}))
{:total_row_count 8751, :included_rows 20, :version 3, :status "ok"}
````

````clojure
> (meta (facts/crosswalk :factual_id "97598010-433f-4946-8fd5-4a6dd1639d77"))
{:total_row_count 13, :included_rows 13, :version 3, :status "ok"}
````

# Schema

You can get the schema for a specific table like this:

````clojure
(schema :restaurants-us)
````

# Handling Bad Responses

The driver uses Slingshot to indicate API errors. If an API error is encountered, a Slingshot stone called factual-error will be thrown.

The factual-error will contain information about the error, including the server response code and any options you used to create the query.

Example:

````clojure
;  (:import [factual.api factual-error])
(try+
	(facts/fetch {:table :places :filters {:factual_id "97598010-433f-4946-8fd5-4a6dd1639d77" :BAD :PARAM}})
	(catch factual-error {code :code message :message opts :opts}
	  (println "Got bad resp code:" code)
	  (println "Message:" message)
	  (println "Opts:" opts)))
````

# An Example of Tying Things Together

Let's create a simple function that finds Places close to a lat/lng, with "cafe" in their name:

````clojure
(defn nearby-cafes
	"Returns up to 12 cafes within 5000 meters of the specified location."
	[lat lon]
	(facts/fetch {:table :places
                      :q "cafe"
	              :filters {:category {:$eq "Food & Beverage"}}
	              :geo {:$circle {:$center [lat lon]
	                              :$meters 5000}}
	              :include_count true
	              :limit 12}))
````

Using our function to get some cafes:

	> (def cafes (nearby-cafes 34.06018 -118.41835))

Let's peek at the metadata:

````clojure
> (meta cafes)
{:total_row_count 26, :included_rows 12, :version 3, :status "ok"}
````

Ok, we got back a full 12 results, and there's actually a total of 26 cafes near us. Let's take a look at a few of the cafes we got back:

````clojure
> (map :name (take 3 cafes))
("Aroma Cafe" "Cafe Connection" "Panini Cafe")
````

That first one, "Aroma Cafe", sounds interesting. Let's see the details:

````clojure
	> (clojure.contrib.pprint/pprint (first cafes))
	{:status "1",
	 :country "US",
	 :longitude -118.423421,
	 :factual_id "eb67e10b-b103-41be-8bb5-e077855b7ae7",
	 :name "Aroma Cafe",
	 :postcode "90064",
	 :locality "Los Angeles",
	 :latitude 34.039792,
	 :region "CA",
	 :address "2530 Overland Ave",
	 :website "http://aromacafe-la.com/",
	 :tel "(310) 836-2919",
	 :category "Food & Beverage"}
````

So I wonder what Yelp has to say about this place. Let's use Crosswalk to find out. Note that we use Aroma Cafe's :factual_id from the above results...

````clojure
	> (facts/crosswalk :factual_id "eb67e10b-b103-41be-8bb5-e077855b7ae7" :only "yelp")
	({:factual_id "eb67e10b-b103-41be-8bb5-e077855b7ae7",
	  :namespace :yelp,
	  :namespace_id "AmtMwS2wCbr3l-_S0d9AoQ",
	  :url "http://www.yelp.com/biz/aroma-cafe-los-angeles"})
````
	  
That gives me the yelp URL for the Aroma Cafe, so I can read up on it on Yelp.com.

Of course, Factual supports other Crosswalked sources besides Yelp. If you look at each row returned by the <tt>crosswalk</tt> function, you'll see there's a <tt>:namespace</tt> in each one. Let's find out what namespaces are available for the Aroma Cafe:

````clojure
	> (map :namespace (facts/crosswalk :factual_id "eb67e10b-b103-41be-8bb5-e077855b7ae7"))
	(:merchantcircle :urbanspoon :yahoolocal :foursquare :yelp ... )
````

Let's create a function that takes a :factual_id and returns a hashmap of each valid namespace associated with its Crosswalk URL:

````clojure
(defn namespaces->urls [factid]
	(into {} (map #(do {(:namespace %) (:url %)})
	  (facts/crosswalk :factual_id factid))))
````

Now we can do this:

````clojure
	> (namespaces->urls "eb67e10b-b103-41be-8bb5-e077855b7ae7")
	{:merchantcircle   "http://www.merchantcircle.com/business/Aroma.Cafe.310-836-2919",
 	 :urbanspoon	   "http://www.urbanspoon.com/r/5/60984/restaurant/West-Los-Angeles/Bali-Place-LA",
 	 :yahoolocal       "http://local.yahoo.com/info-20400708-aroma-cafe-los-angeles",
 	 :foursquare       "https://foursquare.com/venue/46f53d65f964a520f04a1fe3",
 	 :yelp             "http://www.yelp.com/biz/aroma-cafe-los-angeles",	
	 ... }
````

# Debug mode

This driver and the Factual service should always work perfectly, all the time. But in the highly unlikely, almost impossible event that things go wrong, there is a debug mode that will help with troubleshooting.

If you wrap your call(s) with the <tt>with-debug</tt> macro, verbose debug information will be sent to stdout. This will provide details about the request sent to Factual and the response that was returned.

Example use of the <tt>with-debug</tt> macro:

````clojure
(def data (with-debug (fetch {:table :places :q "starbucks" :limit 3})))
````

You can also wrap <tt>with-debug</tt> around the lower-level <tt>get-results</tt> function, like so:

````clojure
(def data (with-debug (get-results "t/places" {:q "starbucks" :limit 3})))
````

# License

The use and distribution terms for this software are covered by the
Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
which can be found in the file LICENSE.html at the root of this distribution.
By using this software in any fashion, you are agreeing to be bound by
the terms of this license.
You must not remove this notice, or any other, from this software.
