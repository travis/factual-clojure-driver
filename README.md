# About

factual-clojure-driver is the officially supported Clojure driver for Factual's API.

Factual's [web-based API](http://developer.factual.com) offers:

* Rich queries across curated datasets U.S. points of interest, global points of interest, U.S. restaurants with long tail attributes, consumer product goods, and more.
* Crosswalk: Translation between Factual IDs, third party IDs, and URLs that represent the same entity across the internet.
* Resolve: An entity resolution API that makes partial records complete, matches one entity against another, and assists in de-duping and normalizing datasets.

# Installation

The driver is hosted at [Clojars](http://clojars.org/factual-clojure-driver). Just add this to your dependencies:

	[factual-clojure-driver "1.3.1"]

# Setup

````clojure
(ns yournamespace.core
  (:require [factual.api :as fact]))
  (fact/factual! "YOUR_FACTUAL_KEY" "YOUR_FACTUAL_SECRET")
````

# Fetch

The <tt>fetch</tt> function supports rich read queries. It takes a hash-map as its argument, which specifies the full query. The only required entry is :table, which must be associated with a valid Factual table name. Optional query parameters, such as row filters and geolocation, are specified with further entries in q.

<tt>fetch</tt> returns a sequence of records, where each record is a hash-map representing a row of results. The returned sequence will have response metatada attached to it, which includes things like API version, status, and extra row count information if it was requested.

Simple example:

````clojure
;; Fetch 3 random Places from Factual
(fact/fetch {:table :places :limit 3})
````
Results might look like:

````clojure
[{:status 1, :country US, :longitude -94.819339, :name Lorillard Tobacco Co., :postcode 66218, ... }
 {:status 1, :country US, :longitude -118.300024, :name Imediahouse, :postcode 90005, ... }
 {:status 1, :country US, :longitude -118.03132, :name El Monte Wholesale Meats, :postcode 91733, ... }]
````

Here's a demo of looking at the metadata of a response:

````clojure
> (def res (fact/fetch {:table :places}))
> (meta res)
{:response {:included_rows 3}, :version 3, :status "ok"}
````

## More Examples

````clojure
;; Sample three business names from the Places dataset (U.S. points of interest):
> (map :name (fact/fetch {:table :places :limit 3}))
("Lorillard Tobacco Co." "Imediahouse" "El Monte Wholesale Meats")
````

````clojure
;; Return rows where region equals "CA"
(fact/fetch {:table :places :filters {"region" "CA"}})
````

````clojure
;; Return rows where name begins with "Starbucks". Return both the data and a total count of the matched rows:
(fact/fetch {:table :places :filters {:name {:$bw "Starbucks"}} :include_count true})
````

````clojure
;; Do a full text search for rows that contain "Starbucks" or "Santa Monica"
(fact/fetch {:table :places :q "Starbucks,Santa Monica"})
````

````clojure
;; Do a full text search for rows that contain "Starbucks" or "Santa Monica" and return rows 20-40
(fact/fetch {:table :places :q "Starbucks,Santa Monica" :offset 20 :limit 20})
````

````clojure
;; Return rows with a name equal to "Stand" within 5000 meters of the specified lat/lng
(fact/fetch {:table :places
	     :filters {:name "Stand"}
	     :geo {:$circle {:$center [34.06018, -118.41835] :$meters 5000}}})
````

````clojure
;; Count all businesses in Chiang Mai, Thailand that are operational and have a telephone number
(get-in
  (meta
	  (fact/fetch {:table :global
       	               :include_count true
	               :filters {:country {:$eq "TH"}
		                 :region {:$eq "Chiang Mai"}
                                 :status {:$eq 1}
                                 :tel {:$blank false}}}))
	  [:response :total_row_count])
````

````clojure
;; Define function that finds restaurants near a given latitude/longitude that deliver dinner, sorted by distance:
(defn deliver-dinner [lat lon]
  (fact/fetch {:table :restaurants-us
	       :filters {:meal_dinner {:$eq true}
	                 :meal_deliver {:$eq true}}
	       :geo {:$circle {:$center [lat lon]
	                       :$meters 4500}}
	       :sort :$distance}))
````

You could use the above function like so:

````clojure
(deliver-dinner 34.039792 -118.423421)
````

## Variations of <tt>fetch</tt>

For added convenience, the <tt>fetch</tt> function supports several other argument variations. For example, this will work:

````clojure
(fact/fetch :places {:limit 3})
````

See the docs on <tt>fetch</tt> for more details.

## Using Fetch with any Factual dataset

<tt>fetch</tt> allows you to specify any valid Factual dataset. E.g.:

````clojure
(fact/fetch {:table :global :limit 12})
(fact/fetch {:table :places :q "starbucks"})
(fact/fetch {:table :restaurants-us :filters {:locality "Los Angeles"}})
(fact/fetch {:table :products-cpg :filters {:brand "The Body Shop"}})
````

# Row Filters

The driver supports all available row filtering logic. Examples:

````clojure
;;; Fetch places whose name field starts with "Starbucks"
(fact/fetch {:table :places :filters {:name {:$bw "Starbucks"}}})
````

````clojure
;;; Fetch U.S. restaurants that have a blank telephone number
(fact/fetch {:table :restaurants-us :filters {:tel {:$blank true}}})
````

````clojure
;;; Fetch U.S. restaurants from one of five states
(fact/fetch {:table :restaurants-us
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

# Facets

The <tt>facets</tt> function gives you row counts for Factual tables, grouped by facets of the data. For example, you may want to query all businesses within 1 mile of a location and for a count of those businesses by category:

````clojure
(fact/facets {:table :restaurants-us :select "category" :geo {:$circle {:$center [34.039792 -118.423421] :$meters 1600}}})
````

The argument to facets is a hash-map of query parameters, and must include entries for <tt>:table</tt> and <tt>:select</tt>. The value for <tt>:select</tt> must be a comma-delimited String indicating which field(s) to facet, e.g. <tt>"locality,region"</tt>.

Not all fields are configured to return facet counts.  To determine what fields you can return facets for, use the <tt>schema</tt> function on the relevant table.  The faceted attribute of the returned schema will let you know.

## Variations of <tt>facets</tt>

For added convenience, the <tt>facets</tt> function supports several other argument variations. For example, this will work:

````clojure
(facets :us-restaurants "locality")
````

See the docs on <tt>facets</tt> for more details.

## More <tt>facets</tt> Examples

````clojure
;; Count Starbucks in the US by city and state
(fact/facets {:table :global :select "locality,region" :q "starbucks" :filters {:country :US}})
````

# Crosswalk

The <tt>crosswalk</tt> function provides a translation between Factual IDs, third party IDs, and URLs that represent the same entity across the internet.

Examples:

````clojure
;; Return all Crosswalk data for the place identified by the specified Factual ID
(fact/crosswalk :factual_id "97598010-433f-4946-8fd5-4a6dd1639d77")
````

````clojure
;; Return Loopt.com Crosswalk data for the place identified by the specified Factual ID
(fact/crosswalk :factual_id "97598010-433f-4946-8fd5-4a6dd1639d77" :only "loopt")
````

````clojure
;; Return all Crosswalk data for the place identified by the specified Foursquare ID
(fact/crosswalk :namespace "foursquare" :namespace_id "4ae4df6df964a520019f21e3")
````

````clojure
;; Return the Yelp.com Crosswalk data for the place identified by a Foursquare ID: 
(fact/crosswalk :namespace "foursquare" :namespace_id "4ae4df6df964a520019f21e3" :only "yelp")
````

# Resolve

Resolve provides an entity resolution API that makes partial records complete, matches one entity against another, and assists in de-duping and normalizing datasets. 

The <tt>resolve</tt> function takes a hash-map of values indicating what you know about a place. It returns the set of potential matches, including a similarity score.

Example:

````clojure
; Find the entity named "McDonald's" with only a specified lat/lng
(fact/resolve {:name "McDonalds", :latitude 34.05671 :longitude -118.42586})
````
	
# Results Metadata

Factual's API returns more than just results rows. It also returns various metadata about the results. You can access this metadata by using Clojure's <tt>meta</tt> function on your results. Examples:

````clojure
> (meta (fact/fetch {:table :places :filters {:name {:$bw "Starbucks"}} :include_count true}))
{:total_row_count 8751, :included_rows 20, :version 3, :status "ok"}
````

````clojure
> (meta (fact/crosswalk :factual_id "97598010-433f-4946-8fd5-4a6dd1639d77"))
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
	(fact/fetch {:table :places :filters {:factual_id "97598010-433f-4946-8fd5-4a6dd1639d77" :BAD :PARAM}})
	(catch factual-error {code :code message :message opts :opts}
	  (println "Got bad resp code:" code)
	  (println "Message:" message)
	  (println "Opts:" opts)))
````

# Example Use Case

Let's create a function that finds Places close to a lat/lng, with "cafe" in their name:

````clojure
(defn nearby-cafes
	"Returns up to 12 cafes within 5000 meters of the specified location."
	[lat lon]
	(fact/fetch {:table :places
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

We got back the full limit of 12 results, and we can see there's a total of 26 cafes near us. Let's take a look at a few of the cafes we got back:

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

No let's use Crosswalk to fine out what Yelp has to say about this place. Note that we use Aroma Cafe's :factual_id from the above results...

````clojure
	> (fact/crosswalk :factual_id "eb67e10b-b103-41be-8bb5-e077855b7ae7" :only "yelp")
	({:factual_id "eb67e10b-b103-41be-8bb5-e077855b7ae7",
	  :namespace :yelp,
	  :namespace_id "AmtMwS2wCbr3l-_S0d9AoQ",
	  :url "http://www.yelp.com/biz/aroma-cafe-los-angeles"})
````
	  
That gives me the yelp URL for the Aroma Cafe, so I can read up on it on Yelp.com.

Of course, Factual supports other Crosswalked sources besides Yelp. If you look at each row returned by the <tt>crosswalk</tt> function, you'll see there's a <tt>:namespace</tt> in each one. Let's find out what namespaces are available for the Aroma Cafe:

````clojure
	> (map :namespace (fact/crosswalk :factual_id "eb67e10b-b103-41be-8bb5-e077855b7ae7"))
	(:merchantcircle :urbanspoon :yahoolocal :foursquare :yelp ... )
````

Let's create a function that takes a :factual_id and returns a hashmap of each valid namespace associated with its Crosswalk URL:

````clojure
(defn namespaces->urls [factid]
	(into {} (map #(do {(:namespace %) (:url %)})
	  (fact/crosswalk :factual_id factid))))
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
(def data (fact/with-debug (fact/fetch {:table :places :q "starbucks" :limit 3})))
````

You can also wrap <tt>with-debug</tt> around the lower-level <tt>get-results</tt> function, like so:

````clojure
(def data (fact/with-debug (fact/get-results "t/places" {:q "starbucks" :limit 3})))
````

# License

The use and distribution terms for this software are covered by the
Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
which can be found in the file LICENSE.html at the root of this distribution.
By using this software in any fashion, you are agreeing to be bound by
the terms of this license.
You must not remove this notice, or any other, from this software.
