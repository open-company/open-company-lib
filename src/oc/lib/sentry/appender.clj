;; mostly copied from https://github.com/yeller/yeller-timbre-appender/blob/master/src/yeller/timbre_appender.clj
;; Thanks Tom!

(ns oc.lib.sentry.appender
  (:require [sentry-clj.core :as sentry]
            [oc.lib.slack :as slack]
            [taoensso.timbre :as timbre]))

(def stacktrace-depth 20)

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
  "Reduce the stacktrace to just `stacktrace-depth` many frames to avoid too big a body for Sentry."
  [ex-map]
  (let [ex (-> ex-map :exception first)
        trimmed (reverse
                  (take stacktrace-depth
                    (-> ex
                      :stacktrace
                      :frames
                      reverse)))]
    (assoc ex-map :exception [(assoc-in ex [:stacktrace :frames] trimmed)])))

(defn appender
  "Sentry timbre appender to send error level messages with a throwable to Sentry."
  [{:keys [dsn] :as opts}]
  (assert dsn (str "The Sentry appender requires a dsn, none given:" dsn))
  {:doc "A timbre appender that sends errors to getsentry.com"
   :min-level :error ; critical this not drop to warn or below as this appender logs at warning level (infinite loop!)
   :enabled? true
   :async? true
   :rate-limit nil
   :fn (fn [args]
         (let [throwable (:?err args)
               message (if throwable
                         (extract-data throwable @(:vargs_ args))
                         (extract-message (:vargs args)))
               payload (cond-> {:message {:message message}
                                :extra {:vargs (:vargs args)}}
                               throwable       (assoc :message {:message (.getMessage throwable)})
                               throwable       (assoc-in [:extra :exception-data] message)
                               throwable       (assoc :throwable throwable)
                               ;; Disable for now
                               ; false     (trim-stacktrace)
                               ; false     (sentry-interfaces/stacktrace throwable)
                               )]
            (try
             (let [r (sentry/send-event payload)]
               (timbre/info "Sentry appender: captured -" r))
             (catch Exception e
               (slack/slack-report e)
               e))))})

(comment

  ;; for repl testing
  (do (require '[taoensso.timbre :as timbre])
      (require '[oc.lib.sentry.appender :reload true])
      (timbre/merge-config! {:appenders {:sentry-appender (oc.lib.sentry.appender/appender
        {:dsn "https://50ad5c0d6ffa47119259403854dc4d5d:2721d28d6622450c85cec0d4b5ea27e8@sentry.io/51845"
         :environment "test"})}})
      (dotimes [_ 1]
        (timbre/error (ex-info "921392813" {:foo 1})
                      {:custom-data {:params {:user-id 1}}})))

  (do (require '[taoensso.timbre :as timbre])
      (require '[oc.lib.sentry.appender :reload true])
      (timbre/merge-config! {:appenders {:sentry-appender (oc.lib.sentry.appender/appender
        {:dsn "https://50ad5c0d6ffa47119259403854dc4d5d:2721d28d6622450c85cec0d4b5ea27e8@sentry.io/51845"})}})
      (dotimes [_ 1]
        (try
          (/ 1 0)
          (catch Exception e
            (timbre/error e)))))

  (do (require '[taoensso.timbre :as timbre])
      (require '[oc.lib.sentry-appender :reload true])
      (timbre/merge-config! {:appenders {:sentry-appender (oc.lib.sentry-appender/sentry-appender
        {:dsn "https://50ad5c0d6ffa47119259403854dc4d5d:2721d28d6622450c85cec0d4b5ea27e8@sentry.io/51845"
        :environment "local"
        :release "your-release-LAST_COMMIT_SHA"})}})
      (dotimes [_ 1]
        (timbre/error "Test just" "string stuff")))

  )