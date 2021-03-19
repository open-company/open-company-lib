(ns oc.lib.component.keep-alive
  (:require [clojure.core.async :refer (chan <!! >!)]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]))

(defn- block! [running-chan]
  (timbre/info "Blocking excecution..")
  (<!! running-chan))

(defn- unblock! [running-chan]
  (timbre/info "Blocking excecution..")
  (>! running-chan true))

(defrecord KeepAlive []

  :load-ns true ; needed for Eastwood linting

  ;; Implement the Lifecycle protocol
  component/Lifecycle

  (start [component]
    (timbre/info "Starting KeepAlive")
    (let [channel (chan)]
      (block! channel)
      (timbre/info "Started KeepAlive")
      (assoc component :running true :running-chan channel)))

  (stop [component]
    (timbre/info "Stopping KeepAlive")
    (unblock! (:running-chan component))
    (timbre/info "Stopped KeepAlive")
    (assoc component :running false :running-chan nil)))
