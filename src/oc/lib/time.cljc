(ns oc.lib.time
  "Functions related to time and timestamps."
  #?(:clj (:require [clj-time.format :as format]
                    [clj-time.core :as time]
                    [clj-time.coerce :as coerce]
                    [defun.core :refer (defun)]
                    [java-time :as jt])
     :cljs (:require [cljs-time.format :as format]
                     [cljs-time.core :as time]
                     [cljs-time.coerce :as coerce]
                     [defun.core :refer (defun)])))

;; ----- Helpers -----

(defn utc-now []
  #?(:clj (time/to-time-zone (time/now) time/utc)
     :cljs (time/to-utc-time-zone (time/now))))

(defn hours-from-now [hours]
  (time/plus (utc-now) (time/hours hours)))

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
  (to-iso (utc-now)))

;; ---- Epoch ----

(defn epoch [t]
  #?(:clj (coerce/to-epoch t)
     :cljs (coerce/to-long (* t 1000))))

(defn now-epoch []
  (epoch (utc-now)))

;; ----- Timestamp in milliseconds -----

(defn from-millis [t]
  (coerce/from-long t))

(defn from-epoch [t]
  (coerce/from-long (* t 1000)))

(defn millis [t]
  (coerce/to-long t))

(defn now-ts []
  (millis (utc-now)))

;; ---- Checks ----

(defn past?
  "Use not-after? as shortcut for before? or equal?."
  [ts]
  (not (time/after? (from-millis ts) (utc-now))))

;; ---- CSV date format ----

#?(:clj
(defun csv-date
  ([]
   (csv-date (time/now) (time/default-time-zone)))
  ([tz]
   (csv-date (time/now) tz))
  ([nil tz]
   (csv-date (time/now) tz))
  ([date-time :guard string? tz]
   (csv-date (jt/instant date-time) tz))
  ([date-time nil]
   (csv-date date-time (time/default-time-zone)))
  ([date-time tz]
   (jt/format "MMM dd YYYY hh:mma" (jt/zoned-date-time date-time "CET"))))
:cljs
(defun csv-date
  ([] (csv-date (utc-now)))
  ([date-time :guard string?]
   (csv-date (from-iso date-time)))
  ([date-time]
   (let [date-format (format/formatter "MMM dd yyyy hh:mma")
         fixed-date-time (time/to-default-time-zone date-time)]
     (format/unparse date-format fixed-date-time)))))