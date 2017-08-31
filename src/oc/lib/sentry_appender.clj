;; mostly copied from https://github.com/yeller/yeller-timbre-appender/blob/master/src/yeller/timbre_appender.clj
;; Thanks Tom!

(ns oc.lib.sentry-appender
  (:require [raven-clj.core :as sentry]
            [raven-clj.interfaces :as sentry-interfaces]
            [taoensso.timbre :as timbre]))

(def *stacktrace-depth* 20)

(defn- extract-ex-data [throwable]
  (if-let [data (ex-data throwable)]
    {:ex-data data}
    {}))

(defn- extract-arg-data [raw-args]
  (if-let [m (first (filter map? raw-args))]
    m
    {}))

(defn- extract-data [throwable raw-args]
  (let [arg-data (extract-arg-data raw-args)
        ex-data (extract-ex-data throwable)]
    (merge
      arg-data
      {:custom-data (merge ex-data (:custom-data arg-data {}))})))

(defn- extract-message [args]
  (clojure.string/join " " (map str args)))

(defn- trim-stacktrace
  [ex-map]
  (let [ex (-> ex-map :exception first)
        trimmed (reverse
                  (take *stacktrace-depth*
                    (-> ex                    
                      :stacktrace
                      :frames
                      reverse)))]
    (assoc ex-map :exception [(assoc-in ex [:stacktrace :frames] trimmed)])))

(defn sentry-appender
  "Sentry timbre appender to send error level messages with a throwable to Sentry."
  [dsn]
  (assert dsn "sentry-appender requires a dsn")
  {:doc "A timbre appender that sends errors to getsentry.com"
   :min-level :error ; critical this not drop to warn or below as this appender logs at warning level (infinite loop!)
   :enabled? true
   :async? true
   :rate-limit nil
   :fn (fn [args]
          (timbre/warn "Sentry appender: invoked")
          (let [throwable @(:?err_ args)
                data      (if throwable
                            (extract-data throwable @(:vargs_ args))
                            (extract-message (:vargs args)))]
            (timbre/warn "Sentry appender: capturing to -" dsn)
            (let [result (if throwable
                            ;; Capture an exception
                            (sentry/capture dsn
                              (-> {:message (.getMessage throwable)}
                                  (assoc-in [:extra :exception-data] data) ; bloating the payload too much?
                                  (sentry-interfaces/stacktrace throwable)
                                  (trim-stacktrace)))
                            ;; Capture just logged information
                            (sentry/capture dsn {:message data}))]
              (timbre/warn "Sentry appender: captured -\n" result))))})

(comment

  ;; for repl testing
  (do (require '[taoensso.timbre :as timbre])
      (require '[oc.lib.sentry-appender :reload true])
      (timbre/merge-config! {:appenders {:sentry-appender (oc.lib.sentry-appender/sentry-appender
        "https://50ad5c0d6ffa47119259403854dc4d5d:2721d28d6622450c85cec0d4b5ea27e8@sentry.io/51845")}})
      (dotimes [_ 1]
        (timbre/error (ex-info "921392813" {:foo 1})
                      {:custom-data {:params {:user-id 1}}})))

  (do (require '[taoensso.timbre :as timbre])
      (require '[oc.lib.sentry-appender :reload true])
      (timbre/merge-config! {:appenders {:sentry-appender (oc.lib.sentry-appender/sentry-appender
        "https://50ad5c0d6ffa47119259403854dc4d5d:2721d28d6622450c85cec0d4b5ea27e8@sentry.io/51845")}})
      (dotimes [_ 1]
        (try
          (/ 1 0)
          (catch Exception e
            (timbre/error e)))))

  (do (require '[taoensso.timbre :as timbre])
      (require '[oc.lib.sentry-appender :reload true])
      (timbre/merge-config! {:appenders {:sentry-appender (oc.lib.sentry-appender/sentry-appender
        "https://50ad5c0d6ffa47119259403854dc4d5d:2721d28d6622450c85cec0d4b5ea27e8@sentry.io/51845")}})
      (dotimes [_ 1]
        (timbre/error "Test just" "string stuff")))

  )