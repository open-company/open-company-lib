(ns oc.lib.lambda.common
  "Utility functions for invoking AWS Lambda functions."
  (:require [amazonica.aws.lambda :as lambda]
            [cheshire.core :as json])
  (:import [java.nio.charset StandardCharsets]))

(defn parse-response
  "Parses response returned by call to `invoke-fn` back into clj data."
  [{:keys [payload] :as response}]
  (-> (.. StandardCharsets/UTF_8 (decode payload) toString)
      (json/parse-string keyword)
      :body
      (json/parse-string keyword)))

(defn invoke-fn
  "Invokes AWS lambda function with given fn-name and payload.
  payload should be clj data."
  [fn-name payload]
  (let [json-payload (json/generate-string payload)]
    (lambda/invoke :function-name fn-name
                   :payload json-payload)))
