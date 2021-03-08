(ns oc.lib.sentry.core
  "Adapter for sentry.io server side capture. Provides a
   oc.lib.sentry.core/init
   ([dsn {:environment production :release oc-production-abcdef :debug false}])
    Returns a function that you can call to capture an invent on Sentry.
    There is no need to store the returned function, to capture an event you can use capture.

   oc.lib.sentry.core/capture
   ([message :guard string?] [kw :guard keyword?] [throwable-event :guard #(instance? Throwable %)] [data :guard map?])
    It compose a map with the following structure: {:message {:message your message or error name/title} :throwable your-throwable}
    and pass it to the saved send event function. Logs an erro if that is not provided and tries to call sentry-clj.core/send-event directly.

   If you use a stuartsierra/component like most of our services you can simply use

   oc.lib.sentry.core/map->SentryCapturer
   ([sentry-config])
    Where sentry-config is map with the following keys: :dsn, :environment, :debug, :release.
    Add this as first dependency of your system to make sure it's initialized as first. This will
    take care of calling init and keeping a reference around. This will also setup the timbre appender so
    every logged error is automatically passed to sentry."
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

(def ^:private user-context-keys [:name :first-name :last-name :avatar-url :auth-source :teams
                                  :email :premium-teams :admin :token-created-at :expire :refresh-url])

(defn ctx->extra
  "Helper function to get some extra informations from the Liberator context map."
  [{user :user :as ctx}]
  (as-> {} extra
      (if (map? user)
        (assoc extra :user (select-keys user user-context-keys))
        (assoc extra :user user))
      (merge extra (select-keys ctx [:status :body :data :method :uri :url :x-forwarded-for]))))

;; ---- Helper functions to capture errors and messages ----

(defonce -sentry-logger (atom nil))

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
                      data)
         capture-fn @-sentry-logger]
     (if (fn? capture-fn)
       (capture-fn fixed-data)
       (do
         (timbre/errorf "Error capturing event to Sentry: %s" data)
         (timbre/debug "Trying to send error anyway via sentry/send-event...")
         (sentry/send-event fixed-data)
         (timbre/debug "... done")))))

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
  (println "DBG create-sentry-logger with dsn" dsn config)
  (if dsn
    (let [send-event-fn (fn [event]
                          (try
                            (sentry/send-event event)
                            (catch Exception e
                              (timbre/errorf "Error submitting event '%s' to Sentry!" event)
                              (timbre/error e))))]
      (println "DBG send-event-fn" send-event-fn)
      (timbre/infof "Initialising Sentry with '%s'." dsn)
      (sentry/init! dsn config)
      (println "DBG sentry initialized" send-event-fn)
      (timbre/merge-config! {:appenders {:sentry (sa/appender send-event-fn config)}})
      (println "DBG timbre appender added")
      send-event-fn)
    (do
      (println "DBG no DSN")
      (timbre/warn "No Sentry DSN provided. Sentry events will be logged locally!")
      (fn [event]
        (timbre/infof "Sentry Event not captured (no DSN) '%s'." event)))))

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
  (println "DBG init" config)
  (println "DBG    already initialized?" @-sentry-logger)
  (if-not @-sentry-logger
    (let [sl (create-sentry-logger config)]
      (println "DBG  logger:" sl)
      (reset! -sentry-logger sl)
      (println "DBG  stored logger:" @-sentry-logger)
      sl)
    @-sentry-logger))

;; ---- Sentry component for our system ----

(defrecord SentryCapturer [dsn release environment deploy debug test-fn]
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
            capture-fn (init config)]
        (timbre/info "[sentry-capturer] started")
        (timbre/debug "[sentry-capturer] log function" capture-fn)
        (when (and test-fn
                   (fn? capture-fn))
          (capture-fn {:message "Test this"}))
        (assoc component :sentry-logger capture-fn))))

  (stop [{:keys [sentry-logger] :as component}]
    (if sentry-logger
      (do
        (timbre/info "[sentry-capturer] stopped")
        (assoc component :sentry-logger nil))
      component)))