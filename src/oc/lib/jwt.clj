(ns oc.lib.jwt
  (:require [defun.core :refer (defun defun-)]
            [schema.core :as schema]
            [clj-jwt.core :as jwt]
            [oc.lib.time :as lib-time]
            [clojure.set :as clj-set]
            [oc.lib.db.common :as db-common]
            [oc.lib.schema :as lib-schema]
            [oc.lib.user :as lib-user]))

(def media-type "application/jwt")

(schema/defn ^:always-validate admin-of :- (schema/maybe [lib-schema/UniqueID])
  "
  Given the user-id of the user, return a sequence of team-ids for the teams the user is an admin of.

  Requires a conn to the auth DB.
  "
  [conn user-id :- lib-schema/UniqueID]
  {:pre [(db-common/conn? conn)]}
  (let [teams (db-common/read-resources conn :teams :admins user-id)]
    (vec (map :team-id teams))))

(schema/defn ^:always-validate premium-teams :- (schema/maybe [lib-schema/UniqueID])
  "
  Given the user-id of the user, return a sequence of team-ids for the teams the user is an admin of.

  Requires a conn to the auth DB.
  "
  [conn :- lib-schema/Conn user-id :- lib-schema/UniqueID]
  (let [user (db-common/read-resource conn :users user-id)]
    (some->> (db-common/read-resources-by-primary-keys conn :teams (:teams user) [:team-id :premium])
            (filter :premium)
            (mapv :team-id))))

(defn name-for
  "Fn moved to lib.user ns. Here for backwards compatability."
  ([user] (lib-user/name-for user))
  ([first last] (lib-user/name-for first last)))

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
      (clj-set/rename-keys slack-org {:bot-user-id :id :bot-token :token})
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
  ([conn user :guard #(empty? (:teams %))] {})

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

(defn- timestamp [t]
  (lib-time/millis t))

(defn expire-time
  "Given a token payload return the expiring date depending on the token content."
  [payload]
  (let [expire-in (if (empty? (:slack-bots payload)) 2 24)]
      (timestamp (lib-time/hours-from-now expire-in))))

(defn creation-time []
  (timestamp (lib-time/utc-now)))

(defn expire
  "Set an expire property in the JWToken payload, longer if there's a bot, shorter if not."
  [payload]
  (assoc payload :expire (expire-time payload)))

(defn creation
  "Set a creation time property in the JWToken payload."
  [payload]
  (assoc payload :token-created-at (creation-time)))

(defn timed-payload [payload]
  (-> payload
      expire
      creation))

(defn encode [payload passphrase]
  (-> payload
      jwt/jwt
      (jwt/sign :HS256 passphrase)
      jwt/to-str))

(defn generate-id-token [claims passphrase]
  (let [payload {:id-token true
                 :secure-uuid (:secure-uuid claims)
                 :org-id (:org-uuid claims)
                 :name (:name claims)
                 :first-name (:first-name claims)
                 :last-name (:last-name claims)
                 :user-id (:user-id claims)
                 :avatar-url (:avatar-url claims)
                 :teams [(:team-id claims)]}]
  (encode payload passphrase)))

(defn generate
  "Create a JSON Web Token from a payload."
  [payload passphrase]
  (let [complete-payload (timed-payload payload)]
    (when-not (:super-user complete-payload) ;; trust the super user
      (schema/validate lib-schema/ValidJWTClaims complete-payload)) ; ensure we only generate valid JWTokens
    (encode complete-payload passphrase)))

(defn check-token
  "Verify a JSON Web Token with the passphrase that was (presumably) used to generate it."
  [token passphrase]
  (try
    (-> token
        jwt/str->jwt
        (jwt/verify passphrase))
    (catch Exception _
      false)))

(defn decode
  "Decode a JSON Web Token"
  [token]
  (jwt/str->jwt token))

(defn valid?
  [token passphrase]
  (try
    (if-let [claims (:claims (decode token))]
      (and (check-token token passphrase)
           (lib-schema/valid? lib-schema/Claims claims))
      false)
    (catch Exception _
      false)))

(defn decode-id-token
  "
  Decode the id-token.
  The first version of the id-token had :team-id key instead of :teams and it was released on production for digest only.
  To avoid breaking those links let's move :team-id into :teams (as list) when the id-token is being decoded.
  "
  [token passphrase]
  (when (check-token token passphrase)
    (let [decoded-token (decode token)
          claims (:claims decoded-token)]
      (if (contains? claims :teams)
        decoded-token
        (assoc-in decoded-token [:claims :teams] [(:team-id claims)])))))

(defun refresh?
  "Return true/false if the JWToken needs to be refreshed.
   Can happen when the token is actually expired or
   if it's an old format: see lib-schema/ValidJWTClaims
   and lib-schema/Claims diffs.."
  ([jwt-claims :guard map?]
   (not (lib-schema/valid? lib-schema/ValidJWTClaims jwt-claims)))
  ([jwtoken :guard string?]
   (refresh? (:claims (decode jwtoken)))))

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

(comment
  (require '[oc.lib.jwt :as jwt])

  (def passphrase "this_is_a_test")
  ;; Generate expired JWT
  (def user-claims {:teams ["1234-1234-1234"]
                    :user-id "4321-4321-4321"
                    :first-name "Mickey"
                    :last-name "Mouse"
                    :email "test@example.com"
                    :refresh-url "https://localhost:3002/users/refresh"
                    :name "Mickey Mouse"
                    :auth-source "email"
                    :avatar-url "https://example.com/avatar"
                    :admin []})

  (def with-premium-teams (assoc user-claims :premium-teams []))

  (def old-jwt (-> user-claims
                   jwt/expire
                   (jwt/encode passphrase)))

  (def new-jwt (-> with-premium-teams
                   (jwt/generate passphrase)))

  (def otf-claims (comp :claims jwt/decode))

  (assert (not (jwt/valid? old-jwt passphrase)) "Old payload is not valid.")

  (assert (and (jwt/valid? old-jwt passphrase)
               (not (jwt/refresh? (otf-claims old-jwt)))) "Old payload is valid, but needs refresh.")

  (assert (not (jwt/valid? new-jwt passphrase)) "New payload is not valid.")

  (assert (and (jwt/valid? new-jwt passphrase)
               (not (jwt/refresh? new-jwt))) "New payload is valid, but needs refresh."))