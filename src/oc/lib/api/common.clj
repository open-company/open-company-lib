(ns oc.lib.api.common
  (:require [clojure.string :as s]
            [defun.core :refer (defun)]
            [taoensso.timbre :as timbre]
            [cheshire.core :as json]
            [liberator.representation :refer (ring-response)]
            [liberator.core :refer (by-method)]
            [oc.lib.jwt :as jwt]))

(def UTF8 "utf-8")

(def malformed true)
(def good-json false)

(def json-mime-type "application/json")
(def text-mime-type "text/plain")

(def help-email "hello@carrot.io")
(def error-msg (str "We've been notified of this error. Please contact " help-email " for additional help."))

;; ----- Ring Middleware -----

(defn wrap-500
  "
  Ring middleware to ensure that in the case of a 500 error response or an exception, we don't leak error
  details in the body of the response.
  "
  [handler]
  (fn [request]
    (try
      (let [response (handler request)]
        (if (= 500 (:status response))
          (assoc response :body error-msg)
          response))
      (catch Throwable t
        (timbre/error t)
        {:status 500 :body error-msg}))))

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

(defn unprocessable-entity-response [reason]
  (ring-response
    {:status 422
      :body reason
      :headers {"Content-Type" (format "text/plain;charset=%s" UTF8)}}))

(defn location-response 
  ([location body media-type] (location-response location body 201 media-type))
  ([location body status media-type]
  (ring-response
    {:body body
     :status status
     :headers {"Location" location
               "Content-Type" (format "%s;charset=%s" media-type UTF8)}})))

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
  (if (= (-> ctx :request :request-method) :options)
    true ; always allow options
    (and (:jwtoken ctx) (:user ctx))))

(defn get-token
  "
  Read supplied JWToken from the Authorization in the request headers.

  Return nil if no JWToken provided.
  "
  [headers]
  (if-let [authorization (or (get headers "Authorization") (get headers "authorization"))]
    (last (s/split authorization #" "))))

(defn read-token
  "Read supplied JWToken from the request headers.

   If a valid token is supplied return a map containing :jwtoken and associated :user.
   If invalid token is supplied return a map containing :jwtoken and false.
   If no Authorization headers are supplied return nil."
  [headers passphrase]
  (when-let [token (get-token headers)]
    (if (jwt/valid? token passphrase)
      {:jwtoken token
       :user (:claims (jwt/decode token))}
      {:jwtoken false})))

(defn allow-anonymous
  "Allow unless there is a JWToken provided and it's invalid."
  [ctx]
  (if (= (-> ctx :request :request-method) :options)
    true ; allows allow options
    (boolean (or (nil? (:jwtoken ctx)) (:jwtoken ctx)))))

(defn allow-authenticated
  "Allow only if a valid JWToken is provided."
  [ctx]
  (if (= (-> ctx :request :request-method) :options)
    true ; always allow options
    (authenticated? ctx)))

;; ----- Resources - see: http://clojure-liberator.github.io/liberator/assets/img/decision-graph.svg

;; verify validity of JWToken if it's provided, but it's not required
(defn anonymous-resource [passphrase] {
  :initialize-context (fn [ctx] (read-token (get-in ctx [:request :headers]) passphrase))
  :authorized? allow-anonymous
  :handle-unauthorized (fn [_] (unauthorized-response))
  :handle-forbidden  (fn [ctx] (if (:jwtoken ctx) (forbidden-response) (unauthorized-response)))})

;; verify validity and presence of required JWToken
(defn authenticated-resource [passphrase] {
  :initialize-context (fn [ctx] (read-token (get-in ctx [:request :headers]) passphrase))
  :authorized? (fn [ctx] (authenticated? ctx))
  :handle-not-found (fn [_] (missing-response))
  :handle-unauthorized (fn [_] (unauthorized-response))
  :handle-forbidden (fn [_] (forbidden-response))})

(def open-company-resource {
  :available-charsets [UTF8]
  :handle-not-found (fn [_] (missing-response))
  :handle-not-implemented (fn [_] (missing-response))
  :handle-exception (fn [{ex :exception}] (timbre/error ex)
                                          (error-response error-msg 500))
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
  :conflict? (fn [_] false)})

(defn open-company-anonymous-resource [passphrase]
  (merge open-company-resource (anonymous-resource passphrase)))

(defn open-company-authenticated-resource [passphrase]
  (merge open-company-resource (authenticated-resource passphrase)))