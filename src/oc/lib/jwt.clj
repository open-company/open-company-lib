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