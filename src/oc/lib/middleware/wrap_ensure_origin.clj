(ns oc.lib.middleware.wrap-ensure-origin)

(defn wrap-ensure-origin
  [handler]
  (fn [request]
    (let [origin-header (get-in request [:headers "origin"])]
      (if (re-find #"(?i)^https:\/\/[staging\.|www\.]*carrot\.io\/?$" origin-header)
        (handler request)
        {:status 403
         :body   "Forbidden: origin not allowed"}))))