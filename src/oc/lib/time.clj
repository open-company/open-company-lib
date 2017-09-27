(ns oc.lib.time
  "Functions related to time and timestamps."
  (:require [clj-time.format :as format]
            [clj-time.core :as time]))

;; ----- ISO 8601 timestamp -----

(def timestamp-format (format/formatters :date-time))

(defn current-timestamp
  "ISO 8601 string timestamp for the current time."
  []
  (format/unparse timestamp-format (time/now)))