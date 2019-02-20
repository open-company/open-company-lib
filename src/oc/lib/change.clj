(ns oc.lib.change
  "Get seen data for a post uuid."
  (:require [clojure.walk :refer (keywordize-keys)]
            [clj-http.client :as http]
            [defun.core :refer (defun)]
            [cheshire.core :as json]
            [taoensso.timbre :as timbre]
            [oc.lib.auth :as auth]))

(def default-on-error [{:name "General" :slug "general"}])

(defn- get-post-options
  [token]
  {:headers {"Authorization" (str "Bearer " token)}})

(defn- get-data
  [request-url token]
  (let [response (http/get request-url (get-post-options token))
        status (:status response)
        success? (= status 200)]
    (timbre/trace "HTTP GET Response:\n" response)
    (if success?
      (-> (:body response) json/parse-string keywordize-keys)
      (timbre/error "HTTP GET failed (" status "):" response))))

(defun seen-data-for
  "
  Retrieve the seen data with different kind of inputs.
  Config:
  - passphrase (optional needed to retrieve the JWT, not needed if JWT is passed)
  - auth-server-url (optional needed to retrieve the JWT, not needed if JWT is passed)
  - service-name
  - change-server-url
  "
  ([config :guard #(and (map? %)
                        (contains? % :change-server-url)
                        (contains? % :auth-server-url)
                        (contains? % :passphrase))
    user-data :guard map?
    post-id]
    (let [jwt (auth/user-token user-data (:auth-server-url config)
              (:passphrase config) (:service-name config))]
      (seen-data-for config jwt post-id)))

  ([config :guard map? jwtoken :guard string? post-id]
    (if-let [body (get-data (str (:change-server-url config) "/change/read/post/" post-id) jwtoken)]
    (do
      (timbre/debug "Seen post data:" body)
      body)
    (do
      (timbre/warn "Unable to retrieve seen data.")
      default-on-error))))