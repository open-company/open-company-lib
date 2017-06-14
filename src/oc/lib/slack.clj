(ns oc.lib.slack
  "Make simple (not web socket) Slack Web API HTTP requests and extract the response."
  (:require [clojure.walk :refer (keywordize-keys)]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [taoensso.timbre :as timbre]))

;; https://www.cs.tut.fi/~jkorpela/chars/spaces.html
(def marker-char (char 8203)) ; U+200B a Unicode zero width space, used to mark comment messages originating with OC

(defn- as-echo
  "
  Anything we send into Slack, include a marker (a Unicode non-width space) so we know it was from us.
  
  This is especially useful when getting incoming message events from Slack.
  "
  [text]
  (str marker-char text))

(defn- as-proxy
  "Manually attribute the message to the person that said it with an English attribution."
  [text author]
  (as-echo (str author " said:\n" text)))

(defn- with-reply
  "Given the text of a message, append a link to a threaded discussion to the message."
  [text channel timestamp]
  (let [ts (clojure.string/replace timestamp #"\." "")]
    (str text "\n\n<https://opencompanyhq.slack.com/conversation/" channel "/p" ts "|Reply>")))

(defn slack-api [method params]
  (timbre/info "Making slack request:" method)
  (let [url (str "https://slack.com/api/" (name method))
        {:keys [status headers body error] :as resp} @(http/get url {:query-params params :as :text})]
    (if error
      (throw (ex-info "Error from Slack API"
                {:method method
                 :params params
                 :status status
                 :body body}))
      (-> body json/decode keywordize-keys))))

(defn get-team-info [token]
  (:team (slack-api :team.info {:token token})))

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
                                :text text
                                :channel channel
                                :unfurl_links false}))

(defn- link-comment-to-thread
  "Update the message in Slack to include a link to a threaded discussion."
  ([token channel timestamp text]
  (slack-api :chat.update {:token token
                           :text (-> text as-echo (with-reply channel timestamp))
                           :ts timestamp
                           :channel channel
                           :parse "none"
                           :unfurl_links false
                           :as-user true}))

  ([token channel timestamp text author]
  (slack-api :chat.update {:token token
                           :text (-> text (as-proxy author) (with-reply channel timestamp))
                           :thread_ts timestamp
                           :channel channel
                           :parse "none"
                           :unfurl_links false})))

(defn echo-comment
  "Pass along a comment to a Slack channel, impersonating the user that authored the comment."
  ([user-token channel text]
  (let [result (slack-api :chat.postMessage {:token user-token
                                             :text (as-echo text)
                                             :channel channel
                                             :unfurl_links false
                                             :as_user true})
        timestamp (:ts result)]
    ;; If the initial message was successfully posted, edit it to include a link to a thread
    (if (and (:ok result) timestamp)
      (link-comment-to-thread user-token channel timestamp text)
      result)))
  
  ([user-token channel timestamp text]
  (slack-api :chat.postMessage {:token user-token
                                :text (as-echo text)
                                :channel channel
                                :thread_ts timestamp
                                :unfurl_links false
                                :as_user true})))

(defn proxy-comment
  "
  Pass along a comment to a Slack channel -or- a thread of a channel, using the bot and mentioning the user that
  authored the comment.
  "
  ([bot-token channel text author]
  (let [result (slack-api :chat.postMessage {:token bot-token
                                             :text (as-proxy text author)
                                             :channel channel
                                             :unfurl_links false})
        timestamp (:ts result)]
    ;; If the initial message was successfully posted, edit it to include a link to a thread
    (if (and (:ok result) timestamp)
      (link-comment-to-thread bot-token channel timestamp text author)
      result)))

  ([bot-token channel timestamp text author]
  (slack-api :chat.postMessage {:token bot-token
                                :text (as-proxy text author)
                                :channel channel
                                :thread_ts timestamp
                                :unfurl_links false})))


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
  (slack/echo-comment user-token (-> user-channels first :id) "Comment as user.")

  ;; Proxy a comment for a user
  (def bot-channels (slack/get-channels bot-token))
  (slack/proxy-comment user-token (-> user-channels first :id) "Comment by a user." "Albert Camus")

  )