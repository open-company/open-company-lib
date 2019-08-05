(ns oc.lib.lambda.common
  (:require [amazonica.aws.lambda :as lambda]
            [cheshire.core :as json]))

(defn invoke-fn
  [fn-name payload]
  (let [json-payload (json/generate-string payload)]
    (lambda/invoke :function-name fn-name
                   :payload json-payload)))
