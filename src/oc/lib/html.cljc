(ns oc.lib.html
  "Functions related to processing HTML."
  (:require [cuerdas.core :as str]
            #?(:clj [jsoup.soup :as soup]))
  #?(:clj (:import [org.owasp.html HtmlPolicyBuilder Sanitizers])))

(defn- thumbnail-elements [body]
  (let [thumbnail-selector "img:not(.emojione):not([data-media-type='image/gif']), iframe"]
    #?(:clj
       (let [parsed-body (soup/parse body)
             els (.select parsed-body thumbnail-selector)]
        {:elements els
         :count (count els)})
       :cljs
       (let [$body (js/$ (str "<div>" body "</div>"))
             els (js->clj (js/$ thumbnail-selector $body))]
         {:elements els
          :count (.-length els)}))))

(defn- $el [el]
  #?(:clj
      el
     :cljs
      (js/$ el)))

(defn- tag-name [el]
  #?(:clj
      (.tagName el)
     :cljs
      (.-tagName el)))

(defn- read-size [size]
  #?(:clj
     (Integer/parseInt (re-find #"\A-?\d+" size))
     :cljs
     size))

(defn first-body-thumbnail
  "
  Given an entry body get the first thumbnail available.
  Thumbnail type: image, video or chart.
  This rely on the similitudes between jQuery and soup parsed objects like the attr function.
  "
  [html-body]
  (let [{els-count :count thumb-els :elements} (thumbnail-elements html-body)
        found (atom nil)]
    (dotimes [el-num els-count]
      (let [el #?(:clj (nth thumb-els el-num) :cljs (aget thumb-els el-num))
            $el ($el el)]
        (when-not @found
          (if (= (str/lower (tag-name el)) "img")
            (let [width (read-size (.attr $el "width"))
                  height (read-size (.attr $el "height"))]
              (when (and (not @found)
                         (or (<= width (* height 2))
                             (<= height (* width 2))))
                (reset! found
                  {:type "image"
                   :thumbnail (if (.attr $el "data-thumbnail")
                                (.attr $el "data-thumbnail")
                                (.attr $el "src"))})))
            (reset! found {:type (.attr $el "data-media-type") :thumbnail (.attr $el "data-thumbnail")})))))
    @found))

(def allowed-block-elements ["span" "img" "a" "iframe" "pre" "code" "div" "mark"])

#?(:clj
   (def user-input-html-policy
     (let [string-array     (fn [sa] (into-array java.lang.String sa))
           iframe-src-regex #"^https://((www\.)?youtube.com|player.vimeo.com|(www\.)?loom.com)/.*"]
       (.. (HtmlPolicyBuilder.)
           ;; -- common --
           (allowCommonBlockElements)
           (allowCommonInlineFormattingElements)
           (allowStyling)
           (allowStandardUrlProtocols)
           (allowElements (string-array allowed-block-elements))
           ;; -- span --
           (allowWithoutAttributes (string-array ["span"]))
           (allowAttributes (string-array ["class"
                                           "data-first-name"
                                           "data-last-name"
                                           "data-slack-username"
                                           "data-user-id"
                                           "data-email"
                                           "data-avatar-url"
                                           "data-found"
                                           "data-auto-link"
                                           "data-href"]))
             (onElements (string-array ["span"]))
           ;; -- images --
           (allowAttributes (string-array ["src"
                                           "alt"
                                           "class"
                                           "data-media-type"
                                           "data-thumbnail"]))
             (onElements (string-array ["img"]))
           ;; -- anchors / links --
           (allowAttributes (string-array ["href"
                                           "target"]))
             (onElements (string-array ["a"]))
             (requireRelNofollowOnLinks)
           ;; -- iframes (embeds) --
           (allowAttributes (string-array ["src"]))
             (matching iframe-src-regex)
             (onElements (string-array ["iframe"]))
           (allowAttributes (string-array ["class"
                                           "width"
                                           "height"
                                           "data-media-type"
                                           "frameborder"
                                           "webkitallowfullscreen"
                                           "mozallowfullscreen"
                                           "allowfullscreen"
                                           "data-thumbnail"
                                           "data-video-type"
                                           "data-video-id"]))
             (onElements (string-array ["iframe"]))
            ;; -- pre --
           (allowAttributes (string-array ["class"
                                           "data-disable-toolbar"]))
             (onElements (string-array ["pre"]))
           ;; -- code --
           (allowAttributes (string-array ["class"
                                           "data-disable-toolbar"]))
             (onElements (string-array ["code"]))
           ;; -- div for polls --
           (allowAttributes (string-array ["class"
                                           "contenteditable"
                                           "data-media-type"
                                           "data-poll-id"
                                           "data-question"
                                           "data-disable-toolbar"
                                           "id"]))
             (onElements (string-array ["div"]))
           ;; -- Mark for highlighter button --
           (allowAttributes (string-array ["class"]))
             (onElements (string-array ["mark"]))
           (toFactory)))))

#?(:clj
   (defn sanitize-html
     "Sanitizes HTML content assumed to have been created by a (untrusted) user."
     [html-str]
     (.sanitize user-input-html-policy html-str)
     ))

#?(:clj
   (defn strip-html-tags
     "Reduces an html string to only its textual content, removing all tags. Takes
     optional args:
       - `:decode-entities?` if true, will decode HTML entities (e.g. &#64;)"
     [html-str & {:keys [decode-entities?] :as opts}]
     (let [policy    (.toFactory (HtmlPolicyBuilder.))
           sanitized (.sanitize policy html-str)]
       (if-not decode-entities?
         sanitized
         (.text (soup/parse sanitized))))))
