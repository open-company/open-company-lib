(ns oc.lib.storage
  "Get list of sections from the storage service."
  (:require [clojure.walk :refer (keywordize-keys)]
            [clj-http.client :as http]
            [defun.core :refer (defun)]
            [cheshire.core :as json]
            [taoensso.timbre :as timbre]
            [oc.lib.auth :as auth]))

(def default-on-error [{:name "General" :slug "general"}])

(defn- get-post-options
  [token]
  {:headers {"Authorization" (str "Bearer " token)
             "origin" "carrot.io"}})

(defn- get-data
  [request-url token]
  (let [response (http/get request-url (get-post-options token))
        status (:status response)
        success? (= status 200)]
    (timbre/trace "HTTP GET Response:\n" response)
    (if success?
      (-> (:body response) json/parse-string keywordize-keys)
      (timbre/error "HTTP GET failed (" status "):" response))))

(defn- link-for [rel links]
  (:href (some #(when (= (:rel %) rel) %) links)))

(defn- board-list [data]
  (timbre/debug "Storage org data:" (:boards data))
  (->> (:boards data)
    (map #(select-keys % [:name :slug :uuid]))
    (remove #(= (:slug %) "drafts"))
    vec))

(defn board-list-for
  "
  Given a set of team-id's, and a user's JWToken, return the list of available boards for that user in the
  org that corresponds to the team-id.
  "
  [storage-server-url team-ids jwtoken]
  (if-let [body (get-data storage-server-url jwtoken)]
    (do
      (timbre/debug "Storage slash data:" (-> body :collection :items))
      (let [orgs (-> body :collection :items)
            org (first (filter #(team-ids (:team-id %)) orgs))
            org-url (link-for "item" (:links org))]
        (if org-url
          (board-list (get-data (str storage-server-url org-url) jwtoken))
          (do
            (timbre/warn "Unable to retrieve board data for:" team-ids "in:" body) 
            default-on-error))))
    (do
      (timbre/warn "Unable to retrieve org data.") 
      default-on-error)))

(defun post-data-for
  "
  Retrieve the data for the specified post from the Storage service.

  Arity/5 but the 2nd argument can be either a map of the user data that will be used for
  a JWToken, or can be a JWToken.

  Config:
  - storage-server-url
  - passphrase (not needed if JWT is passed)
  - auth-server-url (not needed if JWT is passed)
  - service-name (always optional)
  "
  ([config :guard #(and (map? %)
                        (contains? % :storage-server-url)
                        (contains? % :auth-server-url)
                        (contains? % :passphrase))
    user-data :guard map?
    org-slug
    board-id
    post-id]
   (let [jwt (auth/user-token user-data (:auth-server-url config)
              (:passphrase config) (:service-name config))]
    (post-data-for config jwt org-slug board-id post-id)))

  ([config :guard map? jwtoken :guard string? org-slug board-id post-id]
    (if-let [body (get-data (str (:storage-server-url config)) jwtoken)]
    (do
      (timbre/debug "Storage slash data:" (-> body :collection :items))
      (let [orgs (-> body :collection :items)
            org (first (filter #(= org-slug (:slug %)) orgs))]
        (if org
          (let [org-data (get-data (str (:storage-server-url config)
                                        "/orgs/"
                                        (:slug org)) jwtoken)
                data (get-data (str (:storage-server-url config)
                                    "/orgs/"
                                    (:slug org)
                                    "/boards/"
                                    board-id
                                    "/entries/"
                                    post-id) jwtoken)
                board (first (filter #(= (:uuid %) board-id) (:boards org-data)))]
            (-> data
                (assoc :org-uuid (:uuid org-data))
                (assoc :org-slug (:slug org-data))
                (assoc :board-slug (:slug board))
                (assoc :board-name (:name board))))
          (do
            (timbre/warn "Unable to retrieve board data for:" org-slug "in:" body)
            default-on-error))))
    (do
      (timbre/warn "Unable to retrieve org data.")
      default-on-error))))