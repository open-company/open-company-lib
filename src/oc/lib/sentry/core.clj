(ns oc.lib.sentry.core
  (:require [com.stuartsierra.component :as component]
            [defun.core :refer (defun)]
            [cuerdas.core :as s]
            [taoensso.timbre :as timbre]
            [sentry-clj.core :as sentry]
            [sentry-clj.ring :as sentry-ring]
            [oc.lib.sentry.appender :as sa]))

(defun capture
  ([nil]
   (timbre/warn "Failed capturing. \nEvent is empty!"))

  ([data :guard :message]
   (sentry/send-event data))

  ([message :guard string?]
   (capture {:message message}))

  ([throwable-event]
   (capture {:message (.getMessage throwable-event)
             :throwable throwable-event})))

(defun wrap

  ([handler sys-conf :guard :sentry-capturer]
   (wrap handler (:sentry-capturer sys-conf)))

  ([handler sentry-config :guard :dsn]
   (let [{:keys [dsn release environment]} sentry-config]
     (sentry-ring/wrap-report-exceptions handler {})))
                                         ; {:postprocess-fn (fn [req e]
                                         ;                    (cond-> e
                                         ;                     (:environment sentry-config)  (assoc :environment (:environment sentry-config))
                                         ;                     (:release sentry-config)      (assoc :release (:release sentry-config))))})))
  ([handler _]
   (timbre/warn "No Sentry configuration found to wrap the handler.")
   handler))

(defn init [sentry-config]
  (sentry/init! (:dsn sentry-config) sentry-config))

(defrecord SentryCapturer [dsn release environment deploy debug]
  component/Lifecycle

  (start [component]
    (timbre/info "[sentry-capturer] starting...")
    (if (or (not dsn)
            (s/blank? dsn))
      (do
        (timbre/info "[sentry-capturer] started (empty DSN)")
        component)
      (let [config {:dsn dsn
                    :release release
                    :deploy deploy
                    :environment environment
                    :debug debug}
            sentry-client (init config)]
        (timbre/info "[sentry-capturer] started")
        (assoc component :sentry-client sentry-client))))

  (stop [{:keys [sentry-client] :as component}]
    (if sentry-client
      (do
        (timbre/info "[sentry-capturer] stopped")
        (assoc component :sentry-client nil))
      component)))

;; ---- Shorthand for appender function ----

(def sentry-appender sa/appender)