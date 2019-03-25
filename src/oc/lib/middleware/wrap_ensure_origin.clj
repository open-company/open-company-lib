(ns oc.lib.middleware.wrap-ensure-origin)

(def origin-403-response
 {:status 403
  :body   "Forbidden: origin not allowed"})

(defn wrap-ensure-origin
  [handler]
  (fn [request]
    (try
     (let [origin-header (get-in request [:headers "origin"])]
       (if (re-find #"(?i)^https:\/\/[staging\.|www\.]*carrot\.io\/?$" origin-header)
         (handler request)
         origin-403-response))
     (catch java.lang.NullPointerException e
       ;; Origin not provided
       origin-403-response))))