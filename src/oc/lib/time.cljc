(ns oc.lib.time
  "Functions related to time and timestamps."
  #?(:clj (:require [clj-time.format :as format]
                    [clj-time.core :as time]
                    [clj-time.coerce :as coerce])
     :cljs (:require [cljs-time.format :as format]
                     [cljs-time.core :as time]
                     [cljs-time.coerce :as coerce])))

;; ----- ISO 8601 timestamp -----

(def timestamp-format
  (format/formatters :date-time))

(defn to-iso [t]
  (format/unparse timestamp-format t))

(defn from-iso [s]
  (format/parse timestamp-format s))

(defn current-timestamp
  "ISO 8601 string timestamp for the current time."
  []
  (to-iso (time/now)))

;; ----- Timestamp in milliseconds -----

(defn millis [t]
  (coerce/to-long t))

(defn now-ts []
  (millis (time/now)))