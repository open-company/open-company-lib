(ns oc.lib.schema
  "Prismatic schema common data schema fragments."
  (:require [clojure.string :as s]
            [schema.core :as schema]
            [oc.lib.user :as lib-user]
            [oc.lib.time :as lib-time]))

;; ----- Utility functions -----

(def r-k schema/required-key)

(def o-k schema/optional-key)

(defn valid?
  "Wrap Prismatic Schema's exception throwing validation, returning true or false instead."
  [schema-def value]
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

(def unique-id-reg-ex #"^(\d|[a-f]){4}-(\d|[a-f]){4}-(\d|[a-f]){4}$")

(defn unique-id?
  "Is this a 14 character string fragment from a UUID e.g. 51ab-4c86-a474"
  [s]
  (if (and s (string? s) (re-matches unique-id-reg-ex s)) true false))


(def double-unique-id-reg-ex #"^(\d|[a-f]){4}-(\d|[a-f]){4}-(\d|[a-f]){4}(-(\d|[a-f]){4}-(\d|[a-f]){4}-(\d|[a-f]){4})?$")

(defn double-unique-id?
  "Is this a 29 character string fragment from a UUID e.g. 51ab-4c86-a474-15ab-d359-ff12
   that is the composition of two UUIDs"
  [s]
  (if (and s (string? s) (re-matches double-unique-id-reg-ex s)) true false))

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

(def DoubleUniqueID (schema/pred double-unique-id?))

(def ISO8601 (schema/pred #(and (string? %)
                                (re-matches #"(?i)^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(\.\d+)?(([+-]\d\d:\d\d)|Z)?$" %))))

(def EmailAddress (schema/pred valid-email-address?))

(def EmailDomain (schema/pred valid-email-domain?))

(def Author {
  :name NonBlankStr
  :user-id UniqueID
  :avatar-url (schema/maybe schema/Str)})

(def SlackChannel {
  (o-k :type) NonBlankStr
  :slack-org-id NonBlankStr
  :channel-name NonBlankStr
  :channel-id NonBlankStr})

(def SlackThread (merge SlackChannel {
                    :thread NonBlankStr
                    (o-k :bot-token) NonBlankStr}))

(def Conn (schema/pred conn?))

(def User
  "The portion of JWT properties that we care about for authorship attribution"
  {
    :user-id UniqueID
    :name NonBlankStr
    :avatar-url (schema/maybe schema/Str)
    schema/Keyword schema/Any ; and whatever else is in the JWT map
  })

(def SlackUsers
  "`:slack-users` map with entries for each Slack team, keyed by Slack team ID, e.g. `:T1N0ASD`"
  {(o-k :slack-users) {schema/Keyword {:slack-org-id NonBlankStr
                                       :id NonBlankStr
                                       :token NonBlankStr
                                       (o-k :display-name) NonBlankStr
                                       schema/Keyword schema/Any}}}) ;; and whatever else is in here)

(def GoogleUsers
  "`:google-users` map with entries for each Google account."
  {(o-k :google-users) {:id NonBlankStr
                        :token {:access-token NonBlankStr
                                :token-type NonBlankStr
                                :query-param schema/Any
                                :params {:expires_in schema/Any
                                         :id_token NonBlankStr
                                         :scope NonBlankStr}}}})
;; Brand color schema

(defn hex-color? [c]
  (and (string? c)
       (re-matches #"^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$" c)))

(def HEXColor (schema/pred hex-color?))

(def RGBChannel (schema/pred #(<= 0 % 254)))

(def RGBColor
  {:r RGBChannel
   :g RGBChannel
   :b RGBChannel})

(def Color
  {:rgb RGBColor
   :hex HEXColor})

(def OCBrandColor
  {:primary Color
   :secondary Color})

(def BrandColor
  {:light OCBrandColor
   :dark OCBrandColor})

;; JWT Schemas

(def NotExpired
  (schema/pred (comp not lib-time/past?)))

(def SlackBotKey
  (schema/if keyword?
    (schema/pred #(and (keyword? %)
                       ;; In #js keywordize-keys extract these as :"1234-1234-1234" instead of :1234-1234-1234
                       ;; that's why we need to check unique-id inside
                       (or (unique-id? (name %))
                           (->> %
                                name
                                count
                                dec
                                (subs (name %) 1)
                                unique-id?))))
    UniqueID))

(def SlackBotMap
  {:id schema/Str
   :token schema/Str
   :slack-org-id schema/Str})

(def SlackBotValue
  (schema/if map?
    SlackBotMap
    [SlackBotMap]))

;; In some old tokens the the :slack-bots key is an empty vector: []
;; make sure that doesn't break the validation, but only if it's empty
(def SlackBots
  (schema/if sequential? (schema/pred empty?) {SlackBotKey SlackBotValue}))

(def GoogleToken
  {:access-token schema/Str
   :token-type schema/Str
   :query-param schema/Any
   :params {:expires_in schema/Any
            :id_token schema/Str
            :scope schema/Any}})

(def PremiumTeams
  [UniqueID])

(def CreatedAt
  (schema/pred lib-time/past?))

(def BaseClaims
  (merge SlackUsers
         {:user-id UniqueID
          :teams [UniqueID]
          :admin [UniqueID]
          :name schema/Str
          :first-name schema/Str
          :last-name schema/Str
          :avatar-url (schema/maybe schema/Str)
          :email NonBlankStr
          :auth-source schema/Any
          (o-k :slack-id) schema/Str
          (o-k :slack-display-name) schema/Str
          (o-k :slack-token) schema/Str
          (o-k :slack-bots) SlackBots
          (o-k :google-id) schema/Str
          (o-k :google-token) schema/Any
          (o-k :digest-delivery) schema/Any
          (o-k :latest-digest-delivery) schema/Any
          :refresh-url NonBlankStr
          :expire schema/Num
          schema/Keyword schema/Any} ; and whatever else is in the JWT map to make it open for future extensions
         ))

(def Claims
  "Generic claims schema that accept every old jwt plus the new optional keys."
  (merge {;; :premium-teams is not required but tokens w/o it are considered expired
          ;; and will be force-refreshed
          (o-k :premium-teams) PremiumTeams
          ;; Adds a created-at field in prevision of adding a more accurate force-expire
          (o-k :token-created-at) CreatedAt}
         BaseClaims))

(def ValidJWTClaims
  "Represent a valid, non expired, complete JWToken."
  (merge {:expire NotExpired
          :premium-teams PremiumTeams
          :token-created-at CreatedAt}
         BaseClaims))