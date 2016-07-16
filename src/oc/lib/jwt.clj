(ns oc.lib.jwt
  (:require [taoensso.timbre :as timbre]
            [clj-jwt.core :as jwt]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]))

(defn expired?
  [jwt-claims]
  (if-let [expire (:expire jwt-claims)]
    (t/after? (t/now) (tc/from-long expire))
    (timbre/error "No expire field found in JWToken" jwt-claims)))

(defn expire
  "Add an `:expire` field to the JWT data"
  [payload]
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
    (let [jwt (jwt/str->jwt token)]
      (when (expired? (:claims jwt))
        (timbre/error "Request made with expired JWToken" (:claims jwt)))
      (boolean (jwt/verify jwt passphrase)))
    (catch Exception e
      false)))

(defn decode
  "Decode a JSON Web Token"
  [token]
  (jwt/str->jwt token))

(defprotocol IJwt
  (-encode [this payload] "Generate JWT for given payload")
  (-decode [this token] "Decode a given JWT"))

(defrecord JwtCoder [passphrase]
  IJwt
  (-encode [this payload] (generate payload passphrase))
  (-decode [this token] (check-token token passphrase)))