(ns oc.lib.async.watcher
  "
  Track which 'watchers' are 'watching' which resources.

  Use of this watcher is through core/async. A message is sent to the `watcher-chan` to
  register interest, unregister interest and send something to all that have registered interest.

  The sending to registered listeners is the resposibility of the user of this watcher and is
  done by consuming the core.async `sender-chan` from this namespace.
  "
  (:require [clojure.core.async :as async :refer [<! >!!]]
            [defun.core :refer (defun-)]
            [taoensso.timbre :as timbre]))

; ;; ----- core.async -----

(defonce watcher-chan (async/chan 10000)) ; buffered channel
(defonce sender-chan (async/chan 10000)) ; buffered channel

(defonce watcher-go (atom nil))

; ;; ----- Storage atom and functions -----

(def watchers (atom {}))

(defn add-watcher [watch-id client-id]
  (let [item-watchers (or (get @watchers watch-id) #{})
        watcher-ids (or (get @watchers client-id) #{})]
    (swap! watchers assoc client-id (conj watcher-ids watch-id)
                          watch-id (conj item-watchers client-id))))

(defn remove-watcher
  ([client-id]
     (let [watcher-ids (or (get @watchers client-id) #{})]
       (doseq [watch-id watcher-ids]
         (remove-watcher watch-id client-id))))

  ([watch-id client-id]
     (let [item-watchers (or (get @watchers watch-id) #{})]
       (swap! watchers assoc watch-id (disj item-watchers client-id))
       (when (not (contains? (get @watchers watch-id) client-id))
         (swap! watchers dissoc client-id)))))

(defn watchers-for [watch-id]
  (vec (get @watchers watch-id)))

; ;; ----- Event handling -----

(defn send-event
  "Send outbound events to core.async channel"
  [id event payload]
  (timbre/debug "Send request to:" id)
  (>!! sender-chan {:id id :event [event payload]}))

(defun- handle-watch-message
  "Handle 3 types of messages: watch, unwatch, send"
  
  ([message :guard :watch]
  ;; Register interest by the specified client in the specified item by storing the client ID
  (let [watch-id (:watch-id message)
        client-id (:client-id message)]
    (timbre/info "Watch request for:" watch-id "by:" client-id)
    (add-watcher watch-id client-id)))

  ([message :guard :unwatch]
  ;; Unregister interest by the specified client in the specified item by removing the client ID
  (let [watch-id (:watch-id message)
        client-id (:client-id message)]
    (if watch-id
      (do
        (timbre/info "Stop watch request for:" watch-id "by:" client-id)
        (remove-watcher watch-id client-id))
      (do
        (timbre/info "Stop watch request by:" client-id)
        (remove-watcher client-id))
    )))

  ([message :guard :send]
  ;; For every client that's registered interest in the specified item, send them the specified event
  (let [watch-id (:watch-id message)]
    (timbre/info "Send request for:" watch-id)
    (let [client-ids (watchers-for watch-id)]
      (if (empty? client-ids)
        (timbre/debug "No watchers for:" watch-id)
        (timbre/debug "Send request to:" client-ids))
      (doseq [client-id client-ids]
        (send-event client-id (:event message) (:payload message))))))

  ([message]
  (timbre/warn "Unknown request in watch channel" message)))

; ;; ----- Watcher event loop -----

(defn watcher-loop []
  (reset! watcher-go true)
  (async/go (while @watcher-go
    (timbre/debug "Watcher waiting...")
    (let [message (<! watcher-chan)]
      (timbre/debug "Processing message on watcher channel...")
      (if (:stop message)
        (do (reset! watcher-go false) (timbre/info "Watcher stopped."))
        (async/thread
          (try
            (handle-watch-message message)
          (catch Exception e
            (timbre/error e)))))))))

; ;; ----- Component start/stop -----

(defn start
  "Start the core.async channel consumer for watching items."
  []
  (watcher-loop))

(defn stop
  "Stop the core.async channel consumer for watching items."
  []
  (when @watcher-go
    (timbre/info "Stopping watcher...")
    (>!! watcher-chan {:stop true})))