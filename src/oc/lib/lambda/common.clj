(ns oc.lib.lambda.common
  (:require [amazonica.aws.lambda :as lambda]
            [cheshire.core :as json])
  (:import [java.nio.charset StandardCharsets]))

(defn parse-response
  [{:keys [payload] :as response}]
  (-> (.. StandardCharsets/UTF_8 (decode payload) toString)
      (json/parse-string keyword)
      :body
      (json/parse-string keyword)))

(defn invoke-fn
  [fn-name payload]
  (let [json-payload (json/generate-string payload)]
    (lambda/invoke :function-name fn-name
                   :payload json-payload)))
