(ns oc.lib.dynamo.common
  (:require [amazonica.aws.dynamodbv2 :as dynamodbv2]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]))

(defn ttl-epoch
  "Given a ttl value, make sure it's an integer to avoid errors, and return the epoch
   of now plus the ttl in days."
  [ttl]
  (let [;; If ttl value is set from env var it's a string, if it's default is an int
        fixed-ttl (if (string? ttl)
                           (Integer. (re-find #"\d+" ttl))
                           ttl)
        ttl-date (time/plus (time/now) (time/days fixed-ttl))]
    (coerce/to-epoch ttl-date)))

(defn ttl-now
  "Return the current time in epoch."
  []
  (coerce/to-epoch (time/now)))


(defn maybe-enable-ttl
  "Given the dynamoDb options and a table name enable the TTL on the table if it is not already."
  [dynamodb-opts table-name & [ttl-field-name]]
  ;; Skip for local version of DynamoDB, it doesn't support TTL
  (when-not (= (:endpoint dynamodb-opts) "http://localhost:8000")
    (let [fixed-ttl-field-name (or ttl-field-name "ttl")
          ttl-description (dynamodbv2/describe-time-to-live dynamodb-opts :table-name table-name)]
      (if (not= (-> ttl-description :time-to-live-description :time-to-live-status) "ENABLED")
        (do
          (println "Enabling TTL on " table-name)
          (println
            (dynamodbv2/update-time-to-live
              dynamodb-opts
              :table-name table-name
              :time-to-live-specification {:attribute-name fixed-ttl-field-name :enabled true})))
        (println "TTL already enabled on " table-name)))))