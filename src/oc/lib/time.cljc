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

#?(
:clj
(defun format-inst

  ([format-string inst :guard #(instance? org.joda.time.DateTime %)]
   (format/unparse (format/formatter format-string) inst))

  ([format-string inst :guard #(or (jt/zoned-date-time? %) (jt/instant? %))]
   (jt/format (jt/formatter format-string) inst)))

:cljs
(defn format-inst [format-string inst]
  (format/unparse (format/formatter format-string) inst)))

(def csv-date-format "MMM dd YYYY")

#?(
:clj
(defun csv-date
  ([]
   (csv-date (jt/instant) (time/default-time-zone)))
  ([tz]
   (csv-date (jt/instant) tz))
  ([nil tz]
   (csv-date (jt/instant) tz))
  ([date-time :guard string? tz]
   (csv-date (jt/instant date-time) tz))
  ([date-time nil]
   (csv-date date-time (time/default-time-zone)))
  ([date-time tz]
   (format-inst csv-date-format (jt/zoned-date-time date-time tz))))

:cljs
(defun csv-date
  ([] (csv-date (utc-now)))
  ([date-time :guard string?]
   (csv-date (from-iso date-time)))
  ([date-time]
   (format-inst csv-date-format (time/to-default-time-zone date-time)))))

(def csv-date-time-format "MMM dd YYYY hh:mma")

#?(
:clj
(defun csv-date-time
  ([]
  (csv-date-time (jt/instant) (time/default-time-zone)))
  ([tz]
  (csv-date-time (jt/instant) tz))
  ([nil tz]
  (csv-date-time (jt/instant) tz))
  ([date-time :guard string? tz]
  (csv-date-time (jt/instant date-time) tz))
  ([date-time nil]
  (csv-date-time date-time (time/default-time-zone)))
  ([date-time tz]
  (format-inst csv-date-time-format (jt/zoned-date-time date-time tz))))

:cljs
(defun csv-date-time
  ([] (csv-date-time (utc-now)))
  ([date-time :guard string?]
  (csv-date-time (from-iso date-time)))
  ([date-time]
   (format-inst csv-date-time-format (time/to-default-time-zone date-time)))))