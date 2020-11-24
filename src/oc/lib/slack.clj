(ns oc.lib.slack
  "Make simple (not web socket) Slack Web API HTTP requests and extract the response."
  (:require [clojure.walk :refer (keywordize-keys)]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [defun.core :refer (defun)]
            [taoensso.timbre :as timbre]))

;; https://www.cs.tut.fi/~jkorpela/chars/spaces.html
(def marker-char (char 8203)) ; U+200B a Unicode zero width space, used to mark comment messages originating with OC

(defn- with-marker
  "
  Anything we send into Slack, include a marker (a Unicode non-width space) so we know it was from us.
  
  This is especially useful when getting incoming message events from Slack.
  "
  [text]
  (str marker-char text))

(defn report-slack-error [body e]
  (timbre/info "Error parsing Slack response" body)
  (timbre/warn e)
  (throw e))

(defn slack-api [method params]
  (timbre/info "Making slack request:" method)
  (let [url (str "https://slack.com/api/" (name method))
        {:keys [status headers body error] :as resp} @(http/get url {:query-params params :as :text})]
    (if error
      (report-slack-error body (ex-info "Error from Slack API"
                                {:method method
                                 :params params
                                 :status status
                                 :body body}))
      (do 
        (timbre/trace "Slack response:" body)
        (try
          (let [response-body (-> body json/decode keywordize-keys)]
            (if-not (:ok response-body)
              (report-slack-error body (ex-info "Slack request was rejected"
                                         {:body body
                                          :status status
                                          :method method
                                          :params params}))
              response-body))
          (catch com.fasterxml.jackson.core.JsonParseException e
            (report-slack-error body e)))))))

(defn get-team-info [token]
  (:team (slack-api :team.info {:token token})))

(defn get-user-info [token user-id]
  (:user (slack-api :users.info {:token token :user user-id})))

(defn get-users [token]
  (:members (slack-api :users.list {:token token})))

(defn get-dm-channel [token user-id]
  (-> (slack-api :im.open {:token token :user user-id}) :channel :id))

(defn get-channels [token]
  (:channels (slack-api :channels.list {:token token})))

(defn post-message
  "Post a message as the bot."
  [bot-token channel text]
  (slack-api :chat.postMessage {:token bot-token
                                :text (with-marker text)
                                :channel channel
                                :unfurl_links false}))

(defn post-attachments
  "Post attachments as the bot."
  ([bot-token channel attachments]
     (post-attachments bot-token channel attachments ""))
  ([bot-token channel attachments text]
     {:pre [(sequential? attachments)
            (every? map? attachments)]}
     (slack-api :chat.postMessage {:token bot-token
                                   :text (with-marker text)
                                   :attachments (json/encode attachments)
                                   :channel channel})))

(defn post-blocks
  "Post blocks as the bot."
  ([bot-token channel blocks]
     (post-blocks bot-token channel blocks ""))
  ([bot-token channel blocks text]
     {:pre [(sequential? blocks)]}
     (slack-api :chat.postMessage {:token bot-token
                                   :blocks (json/encode blocks)
                                   :text (with-marker text)
                                   :channel channel})))

(defn unfurl-post-url
  "Add data to the url when a Carrot url is posted in slack."
  [user-token channel ts url-data]
  (slack-api :chat.unfurl {:token user-token
                           :channel channel
                           :ts ts
                           :unfurls url-data}))

(defn- slack-timestamp?
  "Slack timestamps look like: 1518175926.000301"
  [value]
  (re-matches #"\d*\.\d*" value))

(defn- post-data
  [token channel text]
  {:token token
   :text (with-marker text)
   :channel channel
   :unfurl_links false})

(defn- echo-data
  [token channel text]
  (assoc (post-data token channel text) :as_user true))

(defun echo-message
  "
  Post a message to a Slack channel -or- a thread of a channel, impersonating the user that authored the message.
  
  A thread is specified by its timestamp.

  Optionally can include 2 text messages, the 2nd will be a reply to the first, creating a new thread.
  "
  ;; Echo to a thread
  ([user-token channel timestamp :guard slack-timestamp? text]
  (slack-api :chat.postMessage (assoc (echo-data user-token channel text) :thread_ts timestamp)))

  ;; Echo to a channel
  ([user-token channel text]
  (slack-api :chat.postMessage (echo-data user-token channel text)))

  ;; Echo to a channel, and reply to it, creating a thread
  ([user-token channel initial-text reply-text]
  (let [result (slack-api :chat.postMessage (echo-data user-token channel initial-text))
        timestamp (:ts result)]
    
    ;; If the initial message was successfully posted, reply to it with the 2nd part of the message
    (when (and (:ok result) timestamp)
      (echo-message user-token channel timestamp reply-text))
    
    result)))

(defun proxy-message
  "
  Post a message to a Slack channel -or- a thread of a channel, using the bot and mentioning the user that
  authored the message.

  A thread is specified by its timestamp.

  Optionally can include 2 text messages, the 2nd will be a reply to the first, creating a new thread.
  "
  ;; Post to a thread
  ([bot-token channel timestamp :guard slack-timestamp? text]
  (slack-api :chat.postMessage (assoc (post-data bot-token channel text) :thread_ts timestamp)))

  ;; Post to a channel
  ([bot-token channel text]
  (slack-api :chat.postMessage (post-data bot-token channel text)))

  ;; Post to a channel, and reply to it, creating a thread
  ([bot-token channel initial-text reply-text]
  (let [result (slack-api :chat.postMessage (post-data bot-token channel initial-text))
        timestamp (:ts result)]
    
    ;; If the initial message was successfully posted, reply to it with the 2nd part of the message
    (when (and (:ok result) timestamp)
      (proxy-message bot-token channel timestamp reply-text))
    
    result)))

(defn message-webhook
  "Send a message to a Slack webhook for monitoring/reporting purposes."
  [webhook from message]
  (let [options {:form-params {:payload
                 (json/encode {:username from
                  :icon_url "https://carrot.io/img/carrot_logo.png"
                  :text message})}}
        {:keys [error]} @(http/post webhook options)]
    (not error)))

(comment

  (require '[oc.lib.slack :as slack] :reload)
  (def bot-token "<bot-token>")

  (slack/get-team-info bot-token)
  
  (slack/get-users bot-token)

  (slack/get-channels bot-token)

  (slack/get-users bot-token)

  ;; Message to channel as a bot
  (def bot-channels (slack/get-channels bot-token))
  (slack/post-message bot-token (-> bot-channels first :id) "Test message from the bot.")

  ;; DM to user as a bot
  (def user-id "<user-id>")
  (def dm-channel (slack/get-dm-channel bot-token user-id))
  (slack/post-message bot-token dm-channel "Test DM from the bot.")

  ;; Echo a comment as a user
  (def user-token "<user-token>")
  (def user-channels (slack/get-channels user-token))
  (slack/echo-message user-token (-> user-channels first :id) "Comment as user." "This is my first comment.")
  (slack/echo-message user-token (-> user-channels first :id) "This is my second comment.")

  ;; Proxy a comment for a user
  (def bot-channels (slack/get-channels bot-token))
  (slack/proxy-message bot-token (-> user-channels first :id) "Albert Camus said:" "This is my first comment.")
  (slack/proxy-message bot-token (-> user-channels first :id) "Albert Camus said: This is my second comment.")

  )