## Unreleased 1.4.3

  * Added integration tests for Unicode support
  * Added use of sosueme/conf/dot-factual; demo and tests now expect auth in ~/.factual/factual-auth.yaml

## 1.4.2

  * Added support for Match
  * Updated resolve to be consistent with Factual's new version
  * Deprecated resolved

## 1.4.1

  * Migrated http client to clj-http
  * Added support for Multi
  * Added support for Submit
  * Added support for Flag
  * Added support for Diffs
  * Fixed tests for Crosswalk

## 1.4.0

  * Removed first class Crosswalk (use it as a table instead)
  * Added support for Monetize
  * Renamed with-debug to debug

## 1.3.1

  * Moved github project from dirtyvagabond to Factual
  * Renamed project to factual-clojure-driver
  * Changed fetch to take a single hash-map as the query specification
  * Renamed funnyplaces-error to factual-error
  * Removed support for CrossRef
  * Removed demo.clj and added real unit tests
  * Upgraded google-api-client to 1.8.0-beta
  * Upgraded clojure to 1.4.0
  * Upgraded slingshot to 0.10.2
  * Upgraded data.json to 0.1.2
  * Added support for Facets
  * Added support for Geopulse
  * Added support for Reverse Geocoder
  * Added docs for using World Geographies table
  * Added debug mode
