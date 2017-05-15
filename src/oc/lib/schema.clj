(ns oc.lib.schema
  "Prismatic schema common data schema fragments."
  (:require [clojure.string :as s]
            [schema.core :as schema]))

;; ----- Utility functions -----

(defn valid?
  "Wrap Prismatic Schema's exception throwing validation, returning true or false instead."
  [data-schema value]
  (try
    (schema/validate data-schema value)
    true
    (catch Exception e
      false)))

(defn uuid-string?
  "Is this string a UUID e.g. ce257963-843b-4dbb-91d3-a96ef6479b81"
  [s]
  (if (and s (string? s) (re-matches #"^(\d|[a-f]){8}-(\d|[a-f]){4}-(\d|[a-f]){4}-(\d|[a-f]){4}-(\d|[a-f]){12}$" s))
    true
    false))

(defn unique-id?
  "Is this a 12 character string fragment from a UUID e.g. 51ab-4c86-a474"
  [s]
  (if (and s (string? s) (re-matches #"^(\d|[a-f]){4}-(\d|[a-f]){4}-(\d|[a-f]){4}$" s)) true false))

(defn valid-email-address?
  "Return true if this is a valid email address according to the regex, otherwise false."
  [email-address]
  (if (and (string? email-address)
           (re-matches #"^[^@]+@[^@\\.]+[\\.].+" email-address))
    true
    false))

(defn valid-email-domain?
  "Return true if this is a valid email domain according to the regex, otherwise false."
  [email-domain]
  (if (and (string? email-domain)
      (re-matches #"^[^@\\.]+[\\.].+" email-domain))
    true
    false))

(defn valid-password?
  "Return true if the password is valid, false if not."
  [password]
  (and (string? password)
       (>= (count password) 5)))

(defn conn?
  "Check if a var is a valid RethinkDB connection map/atom."
  [conn]
  (try
    (if (and 
          (map? conn)
          (:client @conn)
          (:db @conn)
          (:token @conn))
      true
      false)
    (catch Exception e
      false)))

;; ----- Schema -----

(def NonBlankStr (schema/pred #(and (string? %) (not (s/blank? %)))))

(def UUIDStr (schema/pred #(uuid-string? %)))

(def UniqueID (schema/pred #(unique-id? %)))

(def ISO8601 (schema/pred #(and (string? %)
                                (re-matches #"(?i)^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?(([+-]\d\d:\d\d)|Z)?$" %))))

(def EmailAddress (schema/pred valid-email-address?))

(def EmailDomain (schema/pred valid-email-domain?))

(def Author {
  :name NonBlankStr
  :user-id UniqueID
  :avatar-url (schema/maybe schema/Str)})

(def Conn (schema/pred #(conn? %)))