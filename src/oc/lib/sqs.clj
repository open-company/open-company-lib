(ns oc.lib.sqs
  "
  A component to consume messages from an SQS queue with a long poll and pass them off to a handler, deleting them if
  they are processed successfully (no exception) by the handler.

  https://github.com/stuartsierra/component
  "
  (:require [com.stuartsierra.component :as component]
            [com.climate.squeedo.sqs-consumer :as sqs]
            [clojure.core.async :as async]
            [taoensso.timbre :as timbre]))

(defn ack
  "Acknowledge the completion of message handling."
  [done-channel message]
  (async/put! done-channel message))

(defrecord SQSListener [sqs-creds sqs-queue message-handler]
  
  ;; Implement the Lifecycle protocol
  component/Lifecycle
  
  (start [component]
    (timbre/info "Starting SQSListener")
    (assoc component :retriever (sqs/start-consumer sqs-queue message-handler)))

  (stop [component]
    (timbre/info "Stopping SQSListener")
    (when-let [consumer (:retriever component)] 
      (sqs/stop-consumer consumer))
    (dissoc component :retriever)))

(defn sqs-listener [sqs-creds sqs-queue message-handler]
  (map->SQSListener {:sqs-creds sqs-creds :sqs-queue sqs-queue :message-handler message-handler}))

(comment

  (require '[environ.core :refer (env)])
  (require '[com.stuartsierra.component :as component])
  (require '[amazonica.aws.sqs :as sqs2])

  (require '[oc.lib.sqs :as sqs] :reload)

  (def access-creds {:access-key (env :aws-access-key-id)
                     :secret-key (env :aws-secret-access-key)})

  (def sqs-queue "oc-email-dev-sean") ;"replace-me")

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