(ns oc.lib.time
  "Functions related to time and timestamps."
  #?(:clj (:require [clj-time.format :as format]
                    [clj-time.core :as time]
                    [clj-time.coerce :as coerce])
     :cljs (:require [cljs-time.format :as format]
                     [cljs-time.core :as time]
                     [cljs-time.coerce :as coerce])))

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

(defn millis [t]
  (coerce/to-long t))

(defn now-ts []
  (millis (utc-now)))

;; ---- Checks ----

(defn past?
  "Use not-after? as shortcut for before? or equal?."
  [ts]
  (not (time/after? (from-millis ts) (utc-now))))