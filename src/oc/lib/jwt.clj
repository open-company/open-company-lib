(ns oc.lib.jwt
  (:require [clojure.string :as s]
            [if-let.core :refer (if-let* when-let*)]
            [defun.core :refer (defun defun-)]
            [taoensso.timbre :as timbre]
            [schema.core :as schema]
            [clj-jwt.core :as jwt]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [oc.lib.db.common :as db-common]
            [oc.lib.schema :as lib-schema]))

(def media-type "application/jwt")

(def SlackBots {lib-schema/UniqueID [{:id schema/Str :token schema/Str :slack-org-id schema/Str}]})

(def auth-sources #{:email :slack :digest})

(def Claims
  (merge {:user-id lib-schema/UniqueID
          :teams [lib-schema/UniqueID]
          :admin [lib-schema/UniqueID]
          :name schema/Str
          :first-name schema/Str
          :last-name schema/Str
          :avatar-url (schema/maybe schema/Str)
          :email lib-schema/NonBlankStr
          :auth-source (schema/pred #(auth-sources (keyword %)))
          (schema/optional-key :slack-id) schema/Str
          (schema/optional-key :slack-display-name) schema/Str
          (schema/optional-key :slack-token) schema/Str
          (schema/optional-key :slack-bots) SlackBots
          :refresh-url lib-schema/NonBlankStr
          :expire schema/Num
          schema/Keyword schema/Any} ; and whatever else is in the JWT map to make it open for future extensions
         lib-schema/slack-users))

(schema/defn ^:always-validate admin-of :- (schema/maybe [lib-schema/UniqueID])
  "
  Given the user-id of the user, return a sequence of team-ids for the teams the user is an admin of.

  Requires a conn to the auth DB.
  "
  [conn user-id :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (let [teams (db-common/read-resources conn :teams :admins user-id)]              
    (vec (map :team-id teams))))

(defun name-for
  "Make a single `name` field from `first-name` and/or `last-name`."
  ([user] (name-for (:first-name user) (:last-name user)))
  ([first-name :guard s/blank? last-name :guard s/blank?] "")
  ([first-name last-name :guard s/blank?] first-name)
  ([first-name :guard s/blank? last-name] last-name)
  ([first-name last-name] (str first-name " " last-name)))

(defun- bot-for
  "
  Given a Slack org resource, return the bot properties suitable for use in a JWToken, or nil if there's no bot
  for the Slack org.

  Or, given a map of Slack orgs to their bots, and a sequence of Slack orgs, return a sequence of bots.
  "
  ;; Single Slack org case
  ([slack-org]
  (when (and (:bot-user-id slack-org) (:bot-token slack-org))
    ;; Extract and rename the keys for JWToken use
    (select-keys
      (clojure.set/rename-keys slack-org {:bot-user-id :id :bot-token :token})
      [:slack-org-id :id :token])))

  ;; Empty case, no more Slack orgs
  ([_bots _slack-orgs :guard empty? results :guard empty?] nil)
  ([_bots _slack-orgs :guard empty? results] (remove nil? results))

  ;; Many Slack orgs case, recursively get the bot for each org one by one
  ([bots slack-orgs results]
  (bot-for bots (rest slack-orgs) (conj results (get bots (first slack-orgs))))))

(defun bots-for
  "
  Given a user, return a map of configured bots for each of the user's teams, keyed by team-id.

  Requires a conn to the auth DB.
  "
  ([conn user :guard #(empty? (:teams %))] [])

  ([conn user]
  (let [team-ids (:teams user)
        teams (db-common/read-resources-by-primary-keys conn :teams team-ids [:team-id :name :slack-orgs]) ; teams the user is a member of
        teams-with-slack (remove #(empty? (:slack-orgs %)) teams) ; teams with a Slack org
        slack-org-ids (distinct (flatten (map :slack-orgs teams-with-slack))) ; distinct Slack orgs
        slack-orgs (if (empty? slack-org-ids)
                      []
                      ;; bot lookup
                      (db-common/read-resources-by-primary-keys conn :slack_orgs slack-org-ids
                        [:slack-org-id :name :bot-user-id :bot-token]))
        bots (remove nil? (map bot-for slack-orgs)) ; remove slack orgs with no bots
        slack-org-to-bot (zipmap (map :slack-org-id bots) bots) ; map of slack org to its bot
        team-to-slack-orgs (zipmap (map :team-id teams-with-slack)
                                   (map :slack-orgs teams-with-slack)) ; map of team to its Slack org(s)
        team-to-bots (zipmap (keys team-to-slack-orgs)
                             (map #(bot-for slack-org-to-bot % []) (vals team-to-slack-orgs)))] ; map of team to bot(s)
    (into {} (remove (comp empty? second) team-to-bots))))) ; remove any team with no bots

(defn expired?
  "Return true/false if the JWToken is expired."
  [jwt-claims]
  (if-let [expire (:expire jwt-claims)]
    (t/after? (t/now) (tc/from-long expire))
    (timbre/error "No expire field found in JWToken" jwt-claims)))

(defn expire
  "Set an expire property in the JWToken payload, longer if there's a bot, shorter if not."
  [payload]
  (let [expire-by (-> (if (empty? (:slack-bots payload)) 2 24)
                      t/hours t/from-now .getMillis)]
    (assoc payload :expire expire-by)))

(defn generate
  "Create a JSON Web Token from a payload."
  [payload passphrase]
  (let [expiring-payload (expire payload)]
    (when-not (:super-user expiring-payload) ;; trust the super user
      (schema/validate Claims expiring-payload)) ; ensure we only generate valid JWTokens
    (-> expiring-payload
        jwt/jwt
        (jwt/sign :HS256 passphrase)
        jwt/to-str)))

(defn check-token
  "Verify a JSON Web Token with the passphrase that was (presumably) used to generate it."
  [token passphrase]
  (try
    (do
      (-> token
        jwt/str->jwt
        (jwt/verify passphrase))
      true)
    (catch Exception e
      false)))

(defn decode
  "Decode a JSON Web Token"
  [token]
  (jwt/str->jwt token))

(defn valid?
  [token passphrase]
  (try
    (if-let* [check? (check-token token passphrase)
              claims (:claims (decode token))
              expired? (not (expired? claims))]
      (do (schema/validate Claims claims)
          true)
      false)
    (catch Exception e
      false)))

;; Sign/unsign terminology coming from `buddy-sign` project
;; which this namespace should eventually be switched to
;; https://funcool.github.io/buddy-sign/latest/

(defprotocol ITokenSigner
  (-sign [this payload] "Generate JWT for given payload")
  (-unsign [this token] "Decode a given JWT, nil if not verifiable or otherwise broken"))

(defrecord TokenSigner [passphrase]
  ITokenSigner
  (-sign [this payload] (generate payload passphrase))
  (-unsign [this token] (when (check-token token passphrase) (decode token))))