(ns oc.lib.api.common
  (:require [clojure.string :as s]
            [defun.core :refer (defun)]
            [taoensso.timbre :as timbre]
            [cheshire.core :as json]
            [oc.lib.sentry.core :as sentry]
            [environ.core :refer (env)]
            [liberator.representation :refer (ring-response)]
            [liberator.core :refer (by-method)]
            [oc.lib.jwt :as jwt]))

(def UTF8 "utf-8")

(def malformed true)
(def good-json false)

(def json-mime-type "application/json")
(def text-mime-type "text/plain")

;; ----- Prod check -----

(defn prod? []
  ;; Use the following to avoid seeing stack traces on staging
  ;; (#{"production" "prod"} (env :env))
  (#{"production" "prod"} (or (env :environment) "prod")))

;; ----- Ring Middleware -----

(defn- error-stack-trace [exc]
  (when (instance? Throwable exc)
    (s/join "\n" (map str (.getStackTrace exc)))))

(defn- error-string [exc]
  (str (ex-message exc)
       (when-let [st (error-stack-trace exc)]
        (str "\n\n" st))
       (when-let [ed (ex-data exc)]
         (str "\n\n" ed))))

(defn- throwable? [e]
  (instance? Throwable e))

(defn- adjust-response-error
  ([resp-error] (adjust-response-error resp-error false))
  ([resp-error override-default-error-message?]
   (cond override-default-error-message? resp-error
         (prod?) sentry/error-msg
         (throwable? resp-error) (error-string resp-error)
         :else sentry/error-msg)))

(defn- error-from-response [response]
  (adjust-response-error (or (:throwable response)
                             (:error response)
                             (:exception response)
                             (:body response)
                             (:reason response))
                         (:override-default-error-message response)))

(defn- adjust-response-body [response]
  (let [status (int (or (:status response) 0))
        error-status? (or (<= 500 status 599)
                          (= 422 status)
                          (= 0 status))]
    (if error-status?
      (assoc response :body (error-from-response response))
      response)))

(defn wrap-500
  "
  Ring middleware to ensure that in the case of a 500 error response or an exception, we don't leak error
  details in the body of the response.
  "
  [handler]
  (fn [request]
    (try
      (let [response (handler request)]
        (adjust-response-body response))
      (catch Throwable t
        (timbre/error t)
        {:status 500 :body (adjust-response-error t)}))))

;; ----- Responses -----

(defn text-response
  "Helper to format a text ring response"
  ([body status] (text-response body status {}))

  ([body status headers]
  {:pre [(string? body)
         (integer? status)
         (map? headers)]}
  (ring-response {
    :body body
    :status status
    :headers (merge {"Content-Type" text-mime-type} headers)})))

(defun json-response
  "Helper to format a generic JSON body ring response"
  ([body status] (json-response body status json-mime-type {}))

  ([body status headers :guard map?] (json-response body status json-mime-type headers))

  ([body status mime-type :guard string?] (json-response body status mime-type {}))

  ([body :guard #(or (map? %) (sequential? %)) status mime-type headers]
  (json-response (json/generate-string body {:pretty true}) status mime-type headers))

  ([body :guard string? status :guard integer? mime-type :guard string? headers :guard map?]
  (ring-response {:body body
                  :status status
                  :headers (merge {"Content-Type" mime-type} headers)})))

(defn error-response
  "Helper to format a JSON ring response with an error."
  ([error status] (error-response error status {}))

  ([error status headers]
   {:pre [(integer? status)
          (map? headers)]}
   (ring-response {
    :body error
    :status status
    :headers headers})))

(defn blank-response [] (ring-response {:status 204}))

(defn options-response [methods]
  (ring-response {
    :status 204
    :headers {"Allow" (s/join ", " (map s/upper-case (map name methods)))}}))

(defn missing-response
  ([]
  (ring-response {
    :status 404
    :body ""
    :headers {"Content-Type" (format "text/plain;charset=%s" UTF8)}}))
  ([reason]
  (ring-response {
    :status 404
    :body reason
    :headers {"Content-Type" (format "text/plain;charset=%s" UTF8)}})))

(def unauthorized "Not authorized. Provide a Bearer JWToken in the Authorization header.")
(defn unauthorized-response []
  (ring-response {
    :status 401
    :body unauthorized
    :headers {"Content-Type" (format "text/plain;charset=%s" UTF8)}}))

(def forbidden "Forbidden. Provide a Bearer JWToken in the Authorization header that is allowed to do this operation.")
(defn forbidden-response []
  (ring-response {
    :status 403
    :body forbidden
    :headers {"Content-Type" (format "text/plain;charset=%s" UTF8)}}))

(defn unprocessable-entity-response
  ([reason] (unprocessable-entity-response reason nil false))
  ([reason status] (unprocessable-entity-response reason status false))
  ([reason status override-default-error-message?]
  (ring-response
   {:status (or status 422)
    :body (cond (keyword? reason)
                (name reason)
                (seq? reason)
                (json/generate-string reason {:pretty true})
                :else
                (str reason))
    :override-default-error-message override-default-error-message?
    :headers {"Content-Type" (format "%s;charset=%s" (if (seq? reason) json-mime-type text-mime-type) UTF8)}})))

(defn unprocessable-entity-handler [{reason :reason status :status override-default-error-message :override-default-error-message}]
  (let [response-body (if (prod?)
                        sentry/error-msg
                        reason)
        capture-message (cond (seq? reason)
                              "Unprocessable entity"
                              (keyword? reason)
                              (name reason)
                              :else
                              (str reason))]
    (sentry/capture {:throwable (RuntimeException. "422 - Unprocessable entity")
                     :message {:message capture-message}
                     :extra {:reason reason
                             :status status
                             :override-default-error-message override-default-error-message}})
    (unprocessable-entity-response response-body (or status 422) override-default-error-message)))

(defn location-response
  ([location body media-type] (location-response location body 201 media-type))
  ([location body status media-type]
  (ring-response
    {:body body
     :status status
     :headers {"Location" location
               "Content-Type" (format "%s;charset=%s" media-type UTF8)}})))

(defn refresh-token? [ctx]
  (and (:jwtoken ctx)
       (:user ctx)
       (jwt/refresh? (:user ctx))))

(defn refresh-token-response []
  (ring-response {:body "JWToken must be refershed"
                  :status 440
                  :headers {"Content-Type" (format "text/plain;charset=%s" UTF8)}}))

(defn handle-unauthorized [ctx]
  (if (refresh-token? ctx)
    (refresh-token-response)
    (unauthorized-response)))

(defn handle-exception [ctx]
  (let [?err (or (:exception ctx) (:error ctx) (:err ctx))
        err (cond (throwable? ?err)
                  ?err
                  (or (:status ctx) (seq (:body ctx)))
                  (RuntimeException. (str (or (:status ctx) "Unknown response") " error: " (subs (:body ctx) 0 (min 56 (count (:body ctx))))))
                  :else
                  (RuntimeException. "Unkown error"))]
    ;; Use warn to avoid a duplicated sentry event
    (timbre/warn err)
    (sentry/capture {:throwable err
                     :message {:message (str "Liberator handle-exception " (:status ctx))}
                     :extra (select-keys ctx [:status :body :data :method :uri :url])})
    (error-response sentry/error-msg 500)))

;; ----- Validations -----

(defun only-accept
  ([status media-types :guard sequential?] (only-accept status (s/join "," media-types)))
  ([status media-types :guard string?]
  (ring-response
    {:status status
     :body (format "Acceptable media type: %s\nAcceptable charset: %s" media-types UTF8)
     :headers {"Content-Type" (format "text/plain;charset=%s" UTF8)}})))

(defn malformed-json?
  "Read in the body param from the request as a string, parse it into JSON, make sure all the
  keys are keywords, and then return it, mapped to :data as the 2nd value in a vector,
  with the first value indicating it's not malformed. Otherwise just indicate it's malformed."
  ([ctx] (malformed-json? ctx false))
  ([ctx allow-nil?]
  (try
    (if-let [data (-> (get-in ctx [:request :body]) slurp (json/parse-string true))]
      ; handle case of a string which is valid JSON, but still malformed for us (since it's not a map or seq)
      (do (when-not (or (map? data) (seq? data)) (throw (Exception.)))
        [good-json {:data data}]))
    (catch Exception e
      (if allow-nil?
        [good-json {:data {}}]
        (do (timbre/warn "Request body not processable as JSON: " e)
          [malformed]))))))

(defn known-content-type?
  [ctx content-type]
  (if-let [request-type (get-in ctx [:request :headers "content-type"])]
    (= (first (s/split content-type #";")) (first (s/split request-type #";")))
    true))

;; ----- Authentication and Authorization -----

(defn authenticated?
  "Return true if the request contains a valid JWToken"
  [ctx]
  (cond
    (= (-> ctx :request :request-method) :options)
    true ; always allow options
    (refresh-token? ctx)
    false
    :else
    (and (:jwtoken ctx) (:user ctx))))

(defn- get-token-from-headers
  "
  Read supplied JWToken from the Authorization in the request headers.

  Return nil if no JWToken provided.
  "
  [headers]
  (timbre/debug "Getting token from headers")
  (when-let [authorization (or (get headers "Authorization") (get headers "authorization"))]
    (last (s/split authorization #" "))))

(def ^:private -id-token-name "id-token")

(defn- id-token-cookie-name []
  (let [prefix (if (prod?)
                 ""
                 (or (env :oc-web-cookie-prefix) "localhost-"))]
    (str prefix -id-token-name)))

(def ^:private -jwt-name "jwt")

(defn- jwtoken-cookie-name []
  (let [prefix (if (prod?)
                 ""
                 (or (env :oc-web-cookie-prefix) "localhost-"))]
    (str prefix -jwt-name)))

(defn- get-token-from-cookies
  "
  Read supplied JWToken from request cookies.

  Return nil if no JWToken provided.
  "
  [cookies]
  (timbre/debug "Getting token from cookies")
  (or (get-in cookies [(jwtoken-cookie-name) :value])
      (get-in cookies [(id-token-cookie-name) :value])))

(defn- get-token-from-params
  "
  Read supplied JWToken from the request parameters.

  Return nil if no JWToken provided.

  Token in parameters is accepted only for development.
  "
  [params]
  (when-not (prod?)
    (timbre/debug "Getting token from params")
    (or (get params (keyword -jwt-name))
        (get params -jwt-name)
        (get params (keyword -id-token-name))
        (get params -id-token-name))))

(defn get-token [req]
  (timbre/debug "Getting user token from request")
  (or (get-token-from-headers (:headers req))
      (get-token-from-cookies (:cookies req))
      (get-token-from-params (:params req))))

(defn read-token
  "Read supplied JWToken from the request headers.

   If a valid token is supplied containing :super-user return :jwttoken and associated :user.
   If a valid id-token is supplied return a map containing :id-token and associated :user.
   If a valid token is supplied return a map containing :jwtoken and associated :user.
   If invalid token is supplied return a map containing :jwtoken and false.
   If no Authorization headers are supplied return nil."
  [req passphrase]
  (if-let [token (get-token req)]
    (let [decoded-token (jwt/decode token)
          check-token? (jwt/check-token token passphrase)
          valid-token? (jwt/valid? token passphrase)]
      (timbre/debug "Token found")
      (cond
        ;; super-user token
        (and (-> decoded-token :claims :super-user)
            check-token?)
        {:jwtoken decoded-token
        :user (:claims decoded-token)}
        ;; identity token
        (and (-> decoded-token :claims :id-token)
              check-token?)
        {:jwtoken false
        :user (:claims (jwt/decode-id-token token passphrase))
        :id-token token}
        ;; Normmal user token
        valid-token?
        {:jwtoken token
          :user (:claims decoded-token)}
        ;; not valid token
        :else
        {:jwtoken false}))
    (do ;; Return false since no JWToken was found
      (timbre/debug "No token found")
      false)))

(defn allow-id-token
  "Allow options request. Allow jwtoken. Allow id token. Allow anonymous."
  [ctx]
  (cond

   (= (-> ctx :request :request-method) :options)
   true

   (:jwtoken ctx)
   (authenticated? ctx)

   (:id-token ctx)
   (and (:id-token ctx) (:user ctx))

   :else
   false))

(defn allow-authenticated
  "Allow only if a valid JWToken is provided."
  [ctx]
  (if (= (-> ctx :request :request-method) :options)
    true ; always allow options
    (authenticated? ctx)))

(defn allow-anonymous
  "Allow unless there is a JWToken provided and it's invalid."
  [ctx]
  (cond (= (-> ctx :request :request-method) :options)
        true ; always allow options

        (:id-token ctx)
        (allow-id-token ctx)

        (:jwtoken ctx)
        (allow-authenticated ctx)

        :else
        true))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; verify validity of JWToken if it's provided, but it's not required
(defn anonymous-resource [passphrase] {
  :initialize-context (fn [ctx] (read-token (:request ctx) passphrase))
  :authorized? allow-anonymous
  :handle-unauthorized handle-unauthorized
  :handle-exception handle-exception
  :handle-forbidden  (fn [ctx] (if (:jwtoken ctx) (forbidden-response) (unauthorized-response)))})

(defn base-authenticated-resource [passphrase]{
  :initialize-context (fn [ctx] (read-token (:request ctx) passphrase))
  :handle-not-found (fn [_] (missing-response))
  :handle-unauthorized handle-unauthorized
  :handle-exception handle-exception
  :handle-forbidden (fn [_] (forbidden-response))})

(defn id-token-resource [passphrase]
  (merge (base-authenticated-resource passphrase)
   {:authorized? allow-id-token}))

;; verify validity and presence of required JWToken
(defn jwt-resource [passphrase]
  (merge (base-authenticated-resource passphrase)
   {:authorized? allow-authenticated}))

(def open-company-resource {
  :available-charsets [UTF8]
  :handle-not-found (fn [_] (missing-response))
  :handle-not-implemented (fn [_] (missing-response))
  :handle-exception handle-exception
  :allowed-methods [:options :get :put :patch :delete]
  :respond-with-entity? (by-method {
    :options false
    :get true
    :put true
    :patch true
    :delete false})
  :malformed? (by-method {
    :options false
    :get false
    :delete false
    :post (fn [ctx] (malformed-json? ctx))
    :put (fn [ctx] (malformed-json? ctx))
    :patch (fn [ctx] (malformed-json? ctx))})
  :can-put-to-missing? (fn [_] false)
  :conflict? (fn [_] false)
  :handle-unprocessable-entity unprocessable-entity-handler})

(defn open-company-anonymous-resource [passphrase]
  (merge open-company-resource (anonymous-resource passphrase)))

(defn open-company-id-token-resource [passphrase]
  (merge open-company-resource (id-token-resource passphrase)))

(defn open-company-authenticated-resource [passphrase]
  (merge open-company-resource (jwt-resource passphrase)))

(defn rep
  "Add ^:replace meta to the value to avoid Liberator deep merge/concat
  it's value."
  [v]
  (if (instance? clojure.lang.IMeta v)
    (with-meta v {:replace true})
    v))

;; ----- Get WS client id ----

(defn get-client-id-from-context [ctx service-key]
  (get-in ctx [:request :headers service-key]))

(defn get-interaction-client-id [ctx]
  (get-client-id-from-context ctx "oc-interaction-client-id"))

(defn get-change-client-id [ctx]
  (get-client-id-from-context ctx "oc-change-client-id"))

(defn get-notify-client-id [ctx]
  (get-client-id-from-context ctx "oc-notify-client-id"))