(ns oc.lib.auth
  "Uses a magic token to perform requests to the auth service like
   getting a valid JWT or the complete user data."
  (:require [cheshire.core :as json]
            [org.httpkit.client :as http]
            [clojure.walk :refer (keywordize-keys)]
            [oc.lib.jwt :as jwt]))

(defn- user-data-map [user-map]
  (let [initial-map {:user user-map}
        has-user-id (contains? user-map :user-id)
        with-user-id (merge initial-map
                       (when has-user-id
                        {:user-id (:user-id user-map)}))
        has-slack-user-id (contains? user-map :slack-user-id)
        with-slack-user (merge with-user-id
                         (when has-slack-user-id
                           {:slack-user-id (:slack-user-id user-map)
                            :slack-team-id (:slack-team-id user-map)}))]
    with-slack-user))

(defn- magic-token
  [user-map passphrase service-name]
  (jwt/generate (merge (user-data-map user-map)
                 {:super-user true
                  :name service-name
                  :auth-source :services})
    passphrase))

(defn get-options
  [token]
  {:headers {"Content-Type" "application/vnd.open-company.auth.v1+json"
             "Authorization" (str "Bearer " token)}})

(defn user-token [user auth-server-url passphrase service-name]
  (let [token-request
        @(http/get (str auth-server-url "/users/refresh/")
                   (get-options (magic-token user passphrase service-name)))]
    (if (= 201 (:status token-request))
      (:body token-request)
      {:error true
       :status (:status token-request)
       :body (:body token-request)})))

(defn- parse-response [response]
  (-> response
      :body
      json/parse-string
      keywordize-keys))

(defn user-data [user auth-server-url passphrase service-name]
  (let [user-request
        @(http/get (str auth-server-url "/users/" (:user-id user))
                   (get-options (magic-token user passphrase service-name)))]
    (when (= 200 (:status user-request))
      (-> user-request
          parse-response
          (dissoc :links)))))

(defn team-data [{:keys [auth-server-url passphrase service-name]} team-id user-id]
  (let [jwtoken  (magic-token {:user-id user-id} passphrase service-name)
        team-request @(http/get (str auth-server-url "/teams/" team-id)
                                (get-options jwtoken))]
    (when (= 200 (:status team-request))
      (parse-response team-request))))

(defn active-users
  ([user auth-server-url passphrase service-name team-id]
   (active-users (magic-token user passphrase service-name) auth-server-url team-id))
  ([jwtoken auth-server-url team-id]
   (let [active-users-request @(http/get (str auth-server-url "/teams/" team-id "/active-users")
                                         (get-options jwtoken))]
     (when (= 200 (:status active-users-request))
       (-> active-users-request
           parse-response
           (dissoc :links))))))