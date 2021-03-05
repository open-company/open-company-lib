(ns oc.lib.sentry.core
  (:require [com.stuartsierra.component :as component]
            [defun.core :refer (defun)]
            [cuerdas.core :as s]
            [taoensso.timbre :as timbre]
            [sentry-clj.core :as sentry]
            [sentry-clj.ring :as sentry-ring]
            [clojure.stacktrace :as clj-st]
            [oc.lib.sentry.appender :as sa]))

(def help-email "hello@carrot.io")
(def error-msg (str "We've been notified of this error. Please contact " help-email " for additional help."))

;; ---- Helper functions to capture errors and messages ----

(defun capture
  ([nil]
   (capture "Warning: empty capture!"))

  ([message :guard string?]
   (capture {:message {:message message}}))

  ([kw :guard keyword?]
   (capture (name kw)))

  ([throwable-event :guard #(instance? Throwable %)]
   (capture {:message {:message (.getMessage throwable-event)}
             :throwable throwable-event}))

  ([data :guard map?]
   (let [message (:message data)
         fixed-data (if (string? message)
                      (-> data
                          (dissoc :message)
                          (assoc-in [:message :message] message))
                      data)]
     (sentry/send-event fixed-data)))

  ([unknown-data-type]
   (capture {:message {:message "Uknown type"}
             :extra {:data unknown-data-type}})))

;; ---- Helper function to wrap ring handlers with sentry capturer ----

(defn ^:private stack-trace-html [t]
  (str "<pre>" (with-out-str (clj-st/print-stack-trace t)) "</pre>"))

(defn ^:private error-response
  "Return a response with a simple error message on production, instead use the stack trace."
  [config req throwable]
  (assoc req
         :status 500
         :headers (merge (:headers req) {"Content-Type" "text/html"})
         :body (str "<html><body>"
                    (if (#{"prod" "production"} (:environment config))
                      error-msg
                      (stack-trace-html throwable))
                    "</body></html>")))
(defun wrap

  ([handler config :guard :dsn]
   (sentry-ring/wrap-report-exceptions handler {:preprocess-fn #(-> %
                                                                    (assoc :method (name (:request-method %)))
                                                                    (assoc :request-method (name (:request-method %))))
                                                :error-fn (partial error-response config)}))

  ([handler _]
   (timbre/warn "No Sentry configuration found to wrap the handler.")
   handler))

;; ---- Sentry init and setup ----

(defn ^:private create-sentry-logger
  "Create a Sentry Logger using the supplied `dsn`.
   If no `dsn` is supplied, simply log the `event` to a `logger`."
  [{:keys [dsn] :as config}]
  (if dsn
    (do
      (timbre/infof "Initialising Sentry with '%s'." dsn)
      (sentry/init! dsn config)
      (timbre/merge-config! {:appenders {:sentry (sa/appender config)}})
      (fn [event]
        (try
          (sentry/send-event event)
          (catch Exception e
            (timbre/errorf "Error submitting event '%s' to Sentry!" event)
            (timbre/error e)))))
    (do
      (timbre/warn "No Sentry DSN provided. Sentry events will be logged locally!")
      (fn [event]
        (timbre/infof "Sentry Event '%s'." event)))))

(defn init
  "Initialise Sentry with the provided `config` and return a function that can be
   used in your application for logging of interesting events to Sentry, for example:
   ```clojure
   (def sentry-logger (init-sentry {:dsn \"https://abcdefg@sentry.io:9000/2\" :environment \"local\"}))
   (sentry-logger {:message \"Oh No!\" :throwable (RuntimeException. \"Something bad has happened!\")})
   ```
   It is **highly** recommended that a system such as [Juxt
   Clip](https://github.com/juxt/clip), or
   [Integrant](https://github.com/weavejester/integrant), or
   [Component](https://github.com/stuartsierra/component) or another
   lifecycle/dependency manager is used to create and maintain the
   `sentry-logger` to ensure that it is only initialised only once. That
   function reference can then be then used in whichever namespaces that are
   appropriate for your application.
   sentry-clj does not maintain a single stateful instance of Sentry, thus it
   is entirely possible that it can be re-initialised multiple times.  **This
   behaviour is not ideal nor supported**."
  [config]
  (create-sentry-logger config))

;; ---- Sentry component for our system ----

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