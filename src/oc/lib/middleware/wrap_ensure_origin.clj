(ns oc.lib.middleware.wrap-ensure-origin)

(defn wrap-ensure-origin
  [handler]
  (fn [request]
    (let [origin-header (get-in request [:headers "origin"])]
      (if (or (= origin-header "http://localhost:3559")
              (= origin-header "https://staging.carrot.com")
              (= origin-header "https://www.carrot.com")
              (= origin-header "https://carrot.com"))
        (handler request)
        {:status 403
         :body   "Forbidden"}))))