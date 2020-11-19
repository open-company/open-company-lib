(ns oc.lib.schema
  "Prismatic schema common data schema fragments."
  (:require [clojure.string :as s]
            [schema.core :as schema]
            [oc.lib.user :as lib-user]
            #?(:clj  [clj-time.core :as t]
               :cljs [cljs-time.core :as t])
            #?(:clj  [clj-time.coerce :as tc]
               :cljs [cljs-time.coerce :as tc])))

;; ----- Utility functions -----

(def r-k schema/required-key)

(def o-k schema/optional-key)

(defn valid?
  "Wrap Prismatic Schema's exception throwing validation, returning true or false instead."
  [value schema-def]
  (try
    (schema/validate schema-def value)
    true
    #?(:clj  (catch Exception _
              false)
       :cljs (catch :default _
               false))))

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
       (>= (count password) 8)))

(defn conn?
  "Check if a var is a valid RethinkDB connection map/atom."
  [conn]
  (try
    (if (and  (map? conn)
              (:client @conn)
              (:db @conn)
              (:token @conn))
      true
      false)
    #?(:clj  (catch Exception _
               false)
       :cljs (catch :default _
               false))))

(defn name-for
  "Fn moved to lib.user ns. Here for backwards compatability."
  ([user] (lib-user/name-for user))
  ([first last] (lib-user/name-for first last)))

(declare Author)
(defn author-for-user
  "Extract the author from the JWToken claims or DB user."
  [user]
  (let [author (select-keys user (keys Author))]
    (if (s/blank? (:name author))
      (assoc author :name (name-for user))
      author)))

;; ----- Schema -----

(def NonBlankStr (schema/pred #(and (string? %) (not (s/blank? %)))))

(def UUIDStr (schema/pred uuid-string?))

(def UniqueID (schema/pred unique-id?))

(def ISO8601 (schema/pred #(and (string? %)
                                (re-matches #"(?i)^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?(([+-]\d\d:\d\d)|Z)?$" %))))

(def EmailAddress (schema/pred valid-email-address?))

(def EmailDomain (schema/pred valid-email-domain?))

(def Author {
  (r-k :name) NonBlankStr
  (r-k :user-id) UniqueID
  (r-k :avatar-url) (schema/maybe schema/Str)})

(def SlackChannel {
  (o-k :type) NonBlankStr
  (r-k :slack-org-id) NonBlankStr
  (r-k :channel-name) NonBlankStr
  (r-k :channel-id) NonBlankStr})

(def SlackThread (merge SlackChannel {
                    (r-k :thread) NonBlankStr
                    (o-k :bot-token) NonBlankStr}))

(def Conn (schema/pred conn?))

(def User
  "The portion of JWT properties that we care about for authorship attribution"
  {
    (r-k :user-id) UniqueID
    (r-k :name) NonBlankStr
    (r-k :avatar-url) (schema/maybe schema/Str)
    (o-k schema/Keyword) schema/Any ; and whatever else is in the JWT map
  })

(def SlackUsers
  "`:slack-users` map with entries for each Slack team, keyed by Slack team ID, e.g. `:T1N0ASD`"
  {(o-k :slack-users) {(r-k schema/Keyword) {(r-k :slack-org-id) NonBlankStr
                                             (r-k :id) NonBlankStr
                                             (r-k :token) NonBlankStr
                                             (o-k :display-name) NonBlankStr
                                             (o-k schema/Keyword) schema/Any}}}) ;; and whatever else is in here)

(def GoogleUsers
  "`:google-users` map with entries for each Google account."
  {(o-k :google-users) {(r-k :id) NonBlankStr
                        (r-k :token) {(r-k :access-token) NonBlankStr
                                      (r-k :token-type) NonBlankStr
                                      (r-k :query-param) schema/Any
                                      (r-k :params) {(r-k :expires_in) schema/Any
                                                     (r-k :id_token) NonBlankStr
                                                     (r-k :scope) NonBlankStr}}}})
;; Brand color schema

(defn hex-color? [c]
  (and (string? c)
       (re-matches #"^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$" c)))

(def HEXColor (schema/pred hex-color?))

(def RGBChannel (schema/pred #(<= 0 % 254)))

(def RGBColor
  {(r-k :r) RGBChannel
   (r-k :g) RGBChannel
   (r-k :b) RGBChannel})

(def Color
  {(r-k :rgb) RGBColor
   (r-k :hex) HEXColor})

(def OCBrandColor
  {(r-k :primary) Color
   (r-k :secondary) Color})

(def BrandColor
  {(r-k :light) OCBrandColor
   (r-k :dark) OCBrandColor})

;; JWT Schemas

(defn- expired? [epoch]
  (not (t/before? (t/now) (tc/from-long epoch))))

(def NotExpired
  (schema/pred (comp not expired?)))

(def SlackBots
  {(o-k UniqueID) [{(r-k :id) schema/Str (r-k :token) schema/Str (r-k :slack-org-id) schema/Str}]})

(def GoogleToken
  {(r-k :access-token) schema/Str
   (r-k :token-type) schema/Str
   (r-k :query-param) schema/Any
   (r-k :params) {(r-k :expires_in) schema/Any
                  (r-k :id_token) schema/Str
                  (r-k :scope) schema/Any}})

(def PremiumTeams
  [UniqueID])

(def CreatedAt
  (schema/pred #(not (t/before? (t/now) (tc/from-long %)))))

(def BaseClaims
  (merge SlackUsers
         {(r-k :user-id) UniqueID
          (r-k :teams) [UniqueID]
          (r-k :admin) [UniqueID]
          (r-k :name) schema/Str
          (r-k :first-name) schema/Str
          (r-k :last-name) schema/Str
          (r-k :avatar-url) (schema/maybe schema/Str)
          (r-k :email) NonBlankStr
          (r-k :auth-source) schema/Any
          (o-k :slack-id) schema/Str
          (o-k :slack-display-name) schema/Str
          (o-k :slack-token) schema/Str
          (o-k :slack-bots) SlackBots
          (o-k :google-id) schema/Str
          (o-k :google-token) schema/Any
          (o-k :digest-delivery) schema/Any
          (o-k :latest-digest-delivery) schema/Any
          (r-k :refresh-url) NonBlankStr
          (r-k :expire) schema/Num
          (o-k schema/Keyword) schema/Any} ; and whatever else is in the JWT map to make it open for future extensions
         ))

(def Claims
  "Generic claims schema that accept every old jwt, adds optional eys"
  (merge {;; :premium-teams is not required but tokens w/o it are considered expired
          ;; and will be force-refreshed
          (o-k :premium-teams) PremiumTeams
          ;; Adds a created-at field in prevision of adding a more accurate force-expire
          (o-k :created-at) CreatedAt}
         BaseClaims))

(def ValidJWTClaims
  "Represent a valid, non expired, complete JWToken."
  (merge {(r-k :expire) NotExpired
          (r-k :premium-teams) PremiumTeams
          (r-k :created-at) CreatedAt}
         BaseClaims))

(def IdTokenOrValidJWTClaims
  "Or an id-token or a valid, non expired, complete jwt."
  (schema/conditional #(contains? % :id-token)
                      ValidJWTClaims))