(ns oc.lib.sqs
  "
  A component to consume messages from an SQS queue with a long poll and pass them off to a handler, deleting them if
  they are processed successfully (no exception) by the handler.

  https://github.com/stuartsierra/component
  "
  (:require [com.stuartsierra.component :as component]
            [com.climate.squeedo.sqs-consumer :as sqs]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [oc.lib.sentry.core :as sentry]
            [amazonica.aws.s3 :as s3]
            [clojure.string :as cstr]
            [cheshire.core :as json]
            [taoensso.timbre :as timbre]
            [oc.lib.api.common :refer (prod?)]
            [environ.core :refer (env)])
   (:import [java.util.zip GZIPInputStream]))

(def INTENTIONALLY-EMPTY-QUEUE "INTENTIONALLY-EMPTY-QUEUE")

(defn warn-empty-queue [sqs-queue]
  (timbre/debugf "Sending warning for empty queue, prod? %s environemnt: %s" (prod?) (env :environment))
  (if (prod?)
    (timbre/errorf "An empty queue named: %s is being used in %s." sqs-queue (env :environment))
    (timbre/warnf "An empty queue named: %s is being used in %s." sqs-queue (env :environment))))

(defn check-empty-queue [sqs-queue]
  (timbre/debug "Checking empty queue:" sqs-queue)
  (let [queue-is-empty? (or (= INTENTIONALLY-EMPTY-QUEUE sqs-queue)
                            (cstr/blank? sqs-queue))]
    (timbre/debug "Queue empty?" queue-is-empty?)
    (when queue-is-empty?
      (warn-empty-queue sqs-queue))
    queue-is-empty?))

(defn ack
  "Acknowledge the completion of message handling."
  [done-channel message]
  (async/put! done-channel message))

(defn log-handler
  "Message handler wrapper that logs unhandled errors."
  [handler message done-channel]
  (try
    (handler message done-channel)
    (catch Exception e
      (timbre/warn e)
      (sentry/capture e)
      (throw e))))

(defrecord SQSListener [sqs-creds sqs-queue message-handler]
  
  :load-ns true ; needed for Eastwood linting

  ;; Implement the Lifecycle protocol
  component/Lifecycle
  
  (start [component]
    (timbre/info "Starting SQSListener")
    (let [empty-queue? (check-empty-queue sqs-queue)
          retriever (if empty-queue?
                      #(timbre/debugf "[EMPTY_SQS_QUEUE_LISTENER] Disposing message %s" %)
                      (sqs/start-consumer sqs-queue message-handler))]
      (timbre/info "Started SQSListener")
      (when empty-queue?
        (timbre/debugf "Queue %s is empty, SQS consumer started with fake handler" sqs-queue))
      (assoc component :retriever retriever)))

  (stop [component]
    (timbre/info "Stopping SQSListener")
    (when-let [consumer (:retriever component)] 
      (sqs/stop-consumer consumer)
      (timbre/info "Stopped SQSListener"))
    (assoc component :retriever nil)))

(defn sqs-listener 
  ([{:keys [sqs-creds sqs-queue message-handler]}]
  {:pre [(map? sqs-creds)
         (string? sqs-queue)
         (fn? message-handler)]}
  (sqs-listener sqs-creds sqs-queue message-handler))

  ([sqs-creds sqs-queue message-handler]
  (map->SQSListener {:sqs-creds sqs-creds :sqs-queue sqs-queue :message-handler (partial log-handler message-handler)})))

(defn- read-from-s3
  [record]
  (let [bucket (get-in record [:s3 :bucket :name])
        object-key (get-in record [:s3 :object :key])
        s3-parsed (cstr/join
                   "\n"
                   (->
                    (s3/get-object bucket object-key)
                    :object-content
                    (java.util.zip.GZIPInputStream.)
                    io/reader
                    line-seq))]
    (try
      (read-string s3-parsed)
      (catch Exception e
        (json/parse-string s3-parsed true)))))

(defn read-message-body
  "
  Try to parse as json, otherwise use read-string. If message is from S3, read data object.
  "
  [msg]
  (let [parsed-msg (try
                     (json/parse-string msg true)
                     (catch Exception e
                       (read-string msg)))]
    (cond

     (seq (:Records parsed-msg)) ;; from S3 to SQS
     ;; read each record
     (map read-from-s3 (:Records parsed-msg))

     ;; from S3 to SNS
     (and (string? (:Message parsed-msg))
          (seq (:Records (json/parse-string (:Message parsed-msg) true))))
     (map read-from-s3 (:Records (json/parse-string (:Message parsed-msg) true)))

     :default
     [parsed-msg])))

(defn __no-op__ 
  "Ignore: needed for Eastwood linting."
  []
  (component/system-map {}))

(comment

  (require '[environ.core :refer (env)])
  (require '[com.stuartsierra.component :as component])
  (require '[amazonica.aws.sqs :as sqs2])

  (require '[oc.lib.sqs :as sqs] :reload)

  (def access-creds {:access-key (env :aws-access-key-id)
                     :secret-key (env :aws-secret-access-key)})

  (def sqs-queue "replace-me")

  (defn test-handler
    "Handler for testing purposes. Users of this lib will write their own handler."
    [message done-channel]
    (println "Got message:\n" message)
    (println "Oops!")
    (/ 1 0)
    (sqs/ack done-channel message))

  (defn system
    "System for testing purposes. Users of this lib will define their own system."
    [config-options]
    (let [{:keys [sqs-creds sqs-queue sqs-msg-handler]} config-options]
      (component/system-map
        :sqs (sqs/sqs-listener sqs-creds sqs-queue sqs-msg-handler))))

  (def repl-system (system {:sqs-queue sqs-queue
                            :sqs-msg-handler test-handler
                            :sqs-creds access-creds}))

  ;; Test starting a consumer  
  (alter-var-root #'repl-system component/start)

  (sqs2/send-message access-creds sqs-queue "Hello World!")

  ;; Stop the consumer
  (alter-var-root #'repl-system component/stop)

)