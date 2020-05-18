(ns oc.lib.middleware.wrap-ensure-origin
  (:require [clojure.string :as cstr]))

(def origin-403-response
 {:status 403
  :body   "Forbidden: origin not allowed"})

(defn wrap-ensure-origin
  [handler origin-domain]
  (let [escaped-origin-domain (cstr/replace origin-domain #"(\.|/)" "\\\\$1")
        origin-re (re-pattern (str "(?i)^" escaped-origin-domain "\\/?$"))]
    (fn [request]
      (try
       (let [websocket? (:websocket? request)
             origin-header (get-in request [:headers "origin"])]
         (if (or (not websocket?) ; we only check origin on websocket requests
                 (re-find origin-re origin-header))
           (handler request) ; all is well
           origin-403-response)) ; ye shall not pass
       (catch java.lang.NullPointerException e
         ;; Origin not provided, also a fail
         origin-403-response)))))