(ns oc.unit.text
  (:require [midje.sweet :refer :all]
            [oc.lib.text :refer (attribution)]))

(def item "foo")

(def authors [{:name "Sean"} {:name "Sean"} {:name "Nathan"} {:name "Nathan"} {:name "Sean"}
              {:name "Stuart"} {:name "Stuart"} {:name "Iacopo"} {:name "Iacopo"} {:name "Ryan"}])

(facts "about making attributions"
  (attribution 3 1 item (take 1 authors)) => "1 foo by Sean"
  (attribution 3 2 item (take 2 authors)) => "2 foos by Sean"
  (attribution 3 3 item (take 3 authors)) => "3 foos by Sean and Nathan"
  (attribution 3 4 item (take 4 authors)) => "4 foos by Sean and Nathan"
  (attribution 3 6 item (take 6 authors)) => "6 foos by Sean, Nathan and Stuart"
  (attribution 3 8 item (take 8 authors)) => "8 foos by Sean, Nathan, Stuart and others"
  (attribution 3 8 item (take 8 authors)) => "8 foos by Sean, Nathan, Stuart and others"
  (attribution 3 10 item authors) => "10 foos by Sean, Nathan, Stuart and others"
  (attribution 4 10 item authors) => "10 foos by Sean, Nathan, Stuart, Iacopo and others"
  (attribution 5 10 item authors) => "10 foos by Sean, Nathan, Stuart, Iacopo and Ryan"
  (attribution 1 10 item authors) => "10 foos by Sean and others")