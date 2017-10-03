(ns oc.lib.time
  "Functions related to time and timestamps."
  #?(:clj (:require [clj-time.format :as format]
                    [clj-time.core :as time])
     :cljs (:require [cljs-time.format :as cljs-format]
                     [cljs-time.core :as cljs-time])))

;; ----- ISO 8601 timestamp -----

(def timestamp-format
  #?(:clj (format/formatters :date-time)
     :cljs (cljs-format/formatters :date-time)))

(defn current-timestamp
  "ISO 8601 string timestamp for the current time."
  []
  #?(:clj (format/unparse timestamp-format (time/now))
     :cljs (cljs-format/unparse timestamp-format (cljs-time/now))))