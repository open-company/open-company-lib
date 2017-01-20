(ns oc.lib.jwt
  (:require [clojure.string :as string]
            [if-let.core :refer (when-let*)]
            [taoensso.timbre :as timbre]
            [clj-jwt.core :as jwt]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]))

(def media-type "application/jwt")

(defn expired?
  "true/false if the JWToken is expired"
  [jwt-claims]
  (if-let [expire (:expire jwt-claims)]
    (t/after? (t/now) (tc/from-long expire))
    (timbre/error "No expire field found in JWToken" jwt-claims)))

(defn expire [payload]
  "Set an expire property in the JWToken payload, longer if there's a bot, shorter if not"
  (let [expire-by (-> (if (:bot payload) 24 2)
                      t/hours t/from-now .getMillis)]
    (assoc payload :expire expire-by)))

(defn generate
  "Get a JSON Web Token from a payload"
  [payload passphrase]
  (-> payload
      expire
      jwt/jwt
      (jwt/sign :HS256 passphrase)
      jwt/to-str))

(defn check-token
  "Verify a JSON Web Token"
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

(defn read-token
  "Read a JWToken from the HTTP Authorization header"
  [headers]
  (when-let* [auth-header (or (get headers "authorization") (get headers "Authorization"))
              jwt         (last (string/split auth-header #" "))]
    (when (check-token jwt) jwt)))

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