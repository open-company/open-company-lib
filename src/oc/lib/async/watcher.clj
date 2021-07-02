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

(defonce watchers (atom {}))

(defn add-watcher
  "Add a client, make sure the read/write is atomic to avoid race conditions"
  [watch-id client-id]
  (swap! watchers
   #(as-> % updated-watchers
      (update updated-watchers watch-id (fn [watching-client-ids]
                                          (conj (into #{} watching-client-ids) client-id)))
      (update updated-watchers client-id (fn [client-watched-ids]
                                           (conj (into #{} client-watched-ids) watch-id))))))

(defn- remove-watch-id-for-client
  "NB: this works also as remove"
  [client-id watch-map watch-id]
  (as-> watch-map nw
    (if (seq (get nw client-id))
      (update nw client-id #(disj (into #{} %) watch-id))
      nw)
    (if (empty? (get nw client-id))
      (dissoc nw client-id)
      nw)))

(def ^{:private true} remove-client-id-for-watch remove-watch-id-for-client)

(defun remove-watcher
  "Remove watchers for client,
   first signature remove all watchers for a client and remove the client-id itself when done,
   second signature remove a specific watcher only for a client,
   make sure the read/write is atomic to avoid race conditions"
  ([client-id :guard lib-schema/unique-id?]
   (remove-watcher :all client-id))

  ([:all client-id :guard lib-schema/unique-id?]
   (swap! watchers
          #(as-> % updated-watchers
             (reduce (partial remove-watch-id-for-client client-id)
                     updated-watchers (get updated-watchers client-id #{}))
             (dissoc updated-watchers client-id))))

  ([watch-id :guard lib-schema/unique-id? :all]
   (swap! watchers
          #(as-> % updated-watchers
             (reduce (partial remove-client-id-for-watch watch-id)
                     updated-watchers (get updated-watchers watch-id #{}))
             (dissoc updated-watchers watch-id))))

  ([watch-id :guard lib-schema/unique-id? client-id :guard lib-schema/unique-id?]
   (swap! watchers
          #(as-> % updated-watchers
             (remove-watch-id-for-client client-id updated-watchers watch-id)
             (if (empty? (get updated-watchers watch-id))
               (dissoc updated-watchers watch-id)
               updated-watchers)))))

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
  (let [watch-id (:watch-id message)
        sender-client-id (:sender-ws-client-id message)]
    (timbre/info "Send request for:" watch-id "skipping" sender-client-id)
    (let [client-ids (watchers-for watch-id)
          without-sender-client-ids (if sender-client-id
                                     (vec (disj (set client-ids) sender-client-id))
                                     client-ids)]
      (if (empty? without-sender-client-ids)
        (timbre/debug "No watchers for:" watch-id)
        (timbre/debug "Send request to:" without-sender-client-ids))
      (doseq [client-id without-sender-client-ids]
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