(ns oc.lib.schema
  "Prismatic schema common data schema fragments."
  (:require [clojure.string :as s]
            [schema.core :as schema]))
  

(def NonBlankString (schema/pred #(and (string? %) (not (s/blank? %)))))

;; 12 character fragment from a UUID e.g. 51ab-4c86-a474
(def UniqueID (schema/pred #(and (string? %)
                                 (re-matches #"^(\d|[a-f]){4}-(\d|[a-f]){4}-(\d|[a-f]){4}$" %)))) 

(def ISO8601 (schema/pred #(and (string? %)
                                (re-matches #"(?i)^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?(([+-]\d\d:\d\d)|Z)?$" %))))
