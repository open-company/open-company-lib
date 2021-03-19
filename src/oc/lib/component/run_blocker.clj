(ns oc.lib.component.run-blocker
  (:require [clojure.core.async :refer (chan <!! >!)]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]))

(defn- block! [running-chan]
  (timbre/info "Blocking excecution..")
  (<!! running-chan))

(defn- unblock! [running-chan]
  (timbre/info "Blocking excecution..")
  (>! running-chan true))

(defrecord RunBlocker []

  :load-ns true ; needed for Eastwood linting

  ;; Implement the Lifecycle protocol
  component/Lifecycle

  (start [component]
    (timbre/info "Starting RunBlocker")
    (let [channel (chan)]
      (block! channel)
      (timbre/info "Started RunBlocker")
      (assoc component :running true :running-chan channel)))

  (stop [component]
    (timbre/info "Stopping RunBlocker")
    (unblock! (:running-chan component))
    (timbre/info "Stopped RunBlocker")
    (assoc component :running false :running-chan nil)))
