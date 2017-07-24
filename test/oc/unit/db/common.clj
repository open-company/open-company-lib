(ns oc.unit.db.common
  (:require [oc.lib.db.common :as co]
            [midje.sweet :refer :all]))

(facts "about with-timeout macro"
  (let [s "result"] 
    (co/with-timeout 30 s) => s
    (co/with-timeout 30 (do (Thread/sleep 20) s) => s)
    (co/with-timeout 30 (do (Thread/sleep 90) s)) => (throws Exception)))