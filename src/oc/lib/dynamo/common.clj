(ns oc.lib.dynamo.common
  (:require [amazonica.aws.dynamodbv2 :as dynamodbv2]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]))

(defn ttl-epoch
  "
  Given a ttl value, make sure it's an integer to avoid errors, and return the epoch
  of now plus the ttl in days.
  "
  [ttl]
  (let [fixed-ttl (if (string? ttl)
                    (Integer. (re-find #"\d+" ttl)) ; TTL value from env var is a string
                    ttl) ; default is an int
        ttl-date (time/plus (time/now) (time/days fixed-ttl))]
    (coerce/to-epoch ttl-date)))

(defn ttl-now
  "Return the current time in seconds since the UNIX epoch."
  []
  (coerce/to-epoch (time/now)))


(defn maybe-enable-ttl
  "Given the DynamoDB options, and a table name, enable the TTL on the table, if it is not already enabled."
  [dynamodb-opts table-name & [ttl-field-name]]
  ;; Skip for the local version of DynamoDB, since it doesn't support TTL
  (when-not (= (:endpoint dynamodb-opts) "http://localhost:8000")
    (let [fixed-ttl-field-name (or ttl-field-name "ttl")
          ttl-description (dynamodbv2/describe-time-to-live dynamodb-opts :table-name table-name)]
      (if (not= (-> ttl-description :time-to-live-description :time-to-live-status) "ENABLED")
          (println "Enabling TTL on " table-name "\n"
            (dynamodbv2/update-time-to-live
              dynamodb-opts
              :table-name table-name
              :time-to-live-specification {:attribute-name fixed-ttl-field-name :enabled true}))
        (println "TTL already enabled on " table-name)))))