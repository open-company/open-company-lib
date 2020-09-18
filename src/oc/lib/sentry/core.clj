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

  ([handler empty-dsn :guard s/blank?]
   (timbre/warn "Failed sentry wrap: empty DSN")
   handler)

  ([handler sentry-config :guard (comp not s/blank?)]
   (let [{:keys [dsn release environment]} sentry-config]
     (sentry-ring/wrap-report-exceptions handler dsn {})))
                                         ; {:postprocess-fn (fn [req e]
                                         ;                    (cond-> e
                                         ;                     (:environment config)  (assoc :environment (:environment config))
                                         ;                     (:release config)      (assoc :release (:release config))))})))

  ([handler sys-conf :guard :sentry-capturer]
   (recur handler (-> sys-conf :sentry-capturer :config))))

(def sentry-appender sa/appender)

(defn init [{:keys [dsn environment release]}]
  (let [sentry-client (sentry/init! dsn)]
    (when release
      (.setRelease sentry-client release))
    (when environment
      (.setEnvironment sentry-client environment))
    ;; Send unhandled exceptions to log and Sentry
    ;; See https://stuartsierra.com/2015/05/27/clojure-uncaught-exceptions
    (Thread/setDefaultUncaughtExceptionHandler
     (reify Thread$UncaughtExceptionHandler
       (uncaughtException [_ thread ex]
         (timbre/error ex "Uncaught exception on" (.getName thread) (.getMessage ex))
         (when ex
           (sentry/send-event ex)))))
    sentry-client))

(defrecord SentryCapturer [dsn release environment]
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
                    :environment environment}
            sentry (init config)]
        (timbre/info "[sentry-capturer] started")
        (assoc component :sentry-capturer {:handler sentry :config config}))))

  (stop [{:keys [sentry-capturer] :as component}]
    (if sentry-capturer
      (do
        (timbre/info "[sentry-capturer] stopped")
        (dissoc component :sentry-capturer))
      component)))