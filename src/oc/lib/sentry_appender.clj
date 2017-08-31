;; mostly copied from https://github.com/yeller/yeller-timbre-appender/blob/master/src/yeller/timbre_appender.clj
;; Thanks Tom!

(ns oc.lib.sentry-appender
  (:require [raven-clj.core :as sentry]
            [raven-clj.interfaces :as sentry-interfaces]
            [taoensso.timbre :as timbre]))

(defn extract-ex-data [throwable]
  (if-let [data (ex-data throwable)]
    {:ex-data data}
    {}))

(defn extract-arg-data [raw-args]
  (if-let [m (first (filter map? raw-args))]
    m
    {}))

(defn extract-data [throwable raw-args]
  (let [arg-data (extract-arg-data raw-args)
        ex-data (extract-ex-data throwable)]
    (merge
      arg-data
      {:custom-data (merge ex-data (:custom-data arg-data {}))})))

(defn sentry-appender
  "Create a Sentry timbre appender.
   (make-sentry-appender \"YOUR SENTRY DSN\")"
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
                data      (extract-data throwable @(:vargs_ args))]
            (when throwable
              (timbre/warn "Sentry appender: capturing to -" dsn)
              (let [result (sentry/capture dsn
                            (-> {:message (.getMessage throwable)}
                                (assoc-in [:extra :exception-data] data)
                                (sentry-interfaces/stacktrace throwable)))]
                (timbre/warn "Sentry appender: captured -\n" result)))))})

(comment

  ;; for repl testing
  (do (require '[taoensso.timbre :as timbre])
      (require '[oc.lib.sentry-appender :reload true])
      (timbre/merge-config! {:appenders {:sentry-appender (oc.lib.sentry-appender/sentry-appender
        "https://50ad5c0d6ffa47119259403854dc4d5d:2721d28d6622450c85cec0d4b5ea27e8@sentry.io/51845")}})
      (dotimes [_ 1]
        (timbre/error (ex-info "921392813" {:foo 1})
                      {:custom-data {:params {:user-id 1}}})))

  )