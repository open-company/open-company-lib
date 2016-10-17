(ns oc.unit.data.contiguous
  (:require [midje.sweet :refer :all]
            [oc.lib.data.utils :as utils]))

(facts "about returning contiguous periods"

  (facts "degenerate cases"
    (utils/contiguous [] :yearly) => (throws Exception)
    (utils/contiguous [] 42) => (throws Exception)
    (utils/contiguous [] "foos") => (throws Exception)
    (utils/contiguous {} :weekly) => (throws Exception)
    (utils/contiguous "" :monthly) => (throws Exception))

  (facts "when periods are weeks"
    (utils/contiguous [] "weekly") => []
    (utils/contiguous [] :weekly) => []
    (utils/contiguous ["2016-10-10"] :weekly) => ["2016-10-10"]
    (utils/contiguous ["2016-10-10" "2016-10-03"] :weekly) => ["2016-10-10" "2016-10-03"]
    (utils/contiguous ["2016-10-03" "2016-10-10"] "weekly") => ["2016-10-10" "2016-10-03"]
    (utils/contiguous ["2016-10-10" "2016-09-26"] :weekly) => ["2016-10-10"]
    (utils/contiguous ["2016-10-10" "2016-10-03" "2016-09-19" "2016-09-26"] :weekly) =>
      ["2016-10-10" "2016-10-03" "2016-09-26" "2016-09-19"]
    (utils/contiguous ["2016-10-10" "2016-10-03" "2016-09-12" "2016-09-26"] :weekly) =>
      ["2016-10-10" "2016-10-03" "2016-09-26"]
    (utils/contiguous ["2016-10-17" "2016-10-03" "2016-09-12" "2016-09-26"] :weekly) => ["2016-10-17"]
    (utils/contiguous ["2016-10-10" "2016-10-03" "2016-09-12" "2016-09-19"] :weekly) => ["2016-10-10" "2016-10-03"]
    (utils/contiguous ["2016-10-10" "2016-09-12" "2016-09-26" "2016-08-29"] :weekly) => ["2016-10-10"]
    (utils/contiguous ["2016-10-10" "2015-10-03" "2013-09-19" "2014-09-26"] :weekly) => ["2016-10-10"])

  (facts "when periods are months"
    (utils/contiguous [] "monthly") => []
    (utils/contiguous [] :monthly) => []
    (utils/contiguous ["2016-10"]) => ["2016-10"]
    (utils/contiguous ["2016-10" "2016-09"] :monthly) => ["2016-10" "2016-09"]
    (utils/contiguous ["2016-09" "2016-10"] "monthly") => ["2016-10" "2016-09"]
    (utils/contiguous ["2016-10" "2016-08"] :monthly) => ["2016-10"]
    (utils/contiguous ["2016-10" "2016-09" "2016-11" "2016-08"]) => ["2016-11" "2016-10" "2016-09" "2016-08"]
    (utils/contiguous ["2016-10" "2016-09" "2016-11" "2016-07"]) => ["2016-11" "2016-10" "2016-09"]
    (utils/contiguous ["2016-10" "2016-09" "2016-12" "2016-08"]) => ["2016-12"]
    (utils/contiguous ["2016-10" "2016-07" "2016-11" "2016-08"]) => ["2016-11" "2016-10"]
    (utils/contiguous ["2016-12" "2016-06" "2016-08" "2016-10"]) => ["2016-12"]
    (utils/contiguous ["2014-02" "2013-03" "2016-05" "2015-04"]) => ["2016-05"])

  (facts "when periods are quarters"
    (utils/contiguous [] "quarterly") => []
    (utils/contiguous [] :quarterly) => []
    (utils/contiguous ["2016-07"] :quarterly) => ["2016-07"]
    (utils/contiguous ["2016-07" "2016-04"] :quarterly) => ["2016-07" "2016-04"]
    (utils/contiguous ["2016-04" "2016-07"] "quarterly") => ["2016-07" "2016-04"]
    (utils/contiguous ["2016-07" "2016-01"] :quarterly) => ["2016-07"]
    (utils/contiguous ["2016-04" "2016-07" "2016-01" "2016-10"] :quarterly) => ["2016-10" "2016-07" "2016-04" "2016-01"]
    (utils/contiguous ["2016-04" "2016-07" "2015-10" "2016-10"] :quarterly) => ["2016-10" "2016-07" "2016-04"]
    (utils/contiguous ["2016-04" "2016-01" "2015-10" "2016-10"] :quarterly) => ["2016-10"]
    (utils/contiguous ["2015-10" "2016-01" "2016-07" "2016-10"] :quarterly) => ["2016-10" "2016-07"]
    (utils/contiguous ["2016-04" "2015-04" "2015-10" "2016-10"] :quarterly) => ["2016-10"]
    (utils/contiguous ["2014-04" "2015-07" "2013-01" "2016-10"] :quarterly) => ["2016-10"]))