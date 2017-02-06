(ns oc.lib.jwt
  (:require [clojure.string :as string]
            [if-let.core :refer (if-let* when-let*)]
            [taoensso.timbre :as timbre]
            [schema.core :as schema]
            [clj-jwt.core :as jwt]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [oc.lib.schema :as lib-schema]))

(def media-type "application/jwt")

(def SlackBots {lib-schema/UniqueID {:id schema/Str :token schema/Str}})

(def auth-sources #{:email :slack})

(def Claims {
  :user-id lib-schema/UniqueID
  :teams [lib-schema/UniqueID]
  :admin [lib-schema/UniqueID]
  :name schema/Str
  :first-name schema/Str
  :last-name schema/Str
  :avatar-url (schema/maybe schema/Str)
  :email lib-schema/NonBlankStr
  :auth-source (schema/pred #(auth-sources (keyword %)))
  (schema/optional-key :slack-id) schema/Str
  (schema/optional-key :slack-token) schema/Str
  (schema/optional-key :slack-bots) SlackBots
  :expire schema/Num})

(defn expired?
  "Return true/false if the JWToken is expired."
  [jwt-claims]
  (if-let [expire (:expire jwt-claims)]
    (t/after? (t/now) (tc/from-long expire))
    (timbre/error "No expire field found in JWToken" jwt-claims)))

(defn expire [payload]
  "Set an expire property in the JWToken payload, longer if there's a bot, shorter if not."
  (let [expire-by (-> (if (:bot payload) 24 2)
                      t/hours t/from-now .getMillis)]
    (assoc payload :expire expire-by)))

(defn generate
  "Create a JSON Web Token from a payload."
  [payload passphrase]
  (let [expiring-payload (expire payload)]
    (schema/validate Claims expiring-payload) ; ensure we only generate valid JWTokens
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