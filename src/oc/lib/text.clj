(ns oc.lib.text
  "Functions related to processing text."
  (:require [clojure.string :as s]
            [cuerdas.core :as str]
            [oc.lib.html :as oc-html]
            [jsoup.soup :as soup]))

(defn attribution
  "
  Given the number of distinct authors to mention, the number of items, what to call the
  item (needs to pluralize with just an 's'), and a sequence of authors of the items
  to attribute (sequence needs to be distinct'able, and have a `:name` property per author),
  return a text string that attributes the authors to the items.

  E.g.

  (attribution 3 7 'comment' [{:name 'Joe'} {:name 'Joe'} {:name 'Moe'} {:name 'Flo'} {:name 'Flo'} {:name 'Sue'}])
  '7 comments by Joe, Moe, Flo and others'
  "
  [attribution-count item-count item-name authors]
  (let [distinct-authors (distinct authors)
        author-names (map :name (take attribution-count distinct-authors))
        more-authors? (> (count distinct-authors) (count author-names))
        multiple-authors? (> (count author-names) 1)
        author-attribution (cond
                              ;; more distinct authors than we are going to mention
                              more-authors?
                              (str (clojure.string/join ", " author-names) " and others")

                              ;; more than 1 author so last mention needs an "and", not a comma
                              multiple-authors?
                              (str (clojure.string/join ", " (butlast author-names))
                                                        " and "
                                                        (last author-names))

                              ;; just 1 author
                              :else
                              (first author-names))]
    (str item-count " " item-name (when (> item-count 1) "s") " by " author-attribution)))

(defn- clean-body-text [body]
  (-> body
    (s/replace #"&nbsp;" " ")
    (str/strip-tags)
    (str/strip-newlines)))

(def body-words 20)

(defn truncated-body [body]
  (let [clean-body (if-not (clojure.string/blank? body)
                     (clean-body-text (.text (soup/parse body)))
                     "")
        splitted-body (clojure.string/split clean-body #" ")
        truncated-body (filter not-empty
                        (take body-words ;; 20 words is the average sentence
                         splitted-body))
        reduced-body (str (clojure.string/join " " truncated-body)
                      (when (= (count truncated-body) body-words)
                        "..."))]
    reduced-body))

(defn replies-summary-text
  "Given a replies summary map, creates a phrase like:

   - Sean Johnson left replies on updates you care about.
   - Sean Johnson and Stuart Levinson left replies on updates you care about.
   - Sean Johnson, Stuart Levinson and 1 other left 6 replies on updates you care about.
   - Sean Johnson, Stuart Levinson and 2 others left 6 replies on updates you care about."
  [{:keys [comment-count comment-authors entry-count]
    :or {comment-count 0 comment-authors 0 entry-count 0}}]
  (when (pos? comment-count)
    (let [space-join (fn [& parts] (s/join " " parts))
          author-name (fn [n & [suffix]] (-> comment-authors (nth n) :name (str suffix)))
          authors-string (case (count comment-authors)
                          1 (author-name 0)
                          2 (space-join (author-name 0) "and" (author-name 1))
                          3 (space-join (author-name 0 ",") (author-name 1) "and 1 other")
                            (space-join (author-name 0 ",") (author-name 1 ",") "and" (- (count comment-authors) 2) "others"))
          replies-string (case comment-count
                          1 "left a"
                            "left")
          replies-word (case comment-count
                          1 "reply"
                            "replies")
          updates-string (case entry-count
                          1 "on an update"
                            "on updates")
          care-string "you care about."]
     [:p.digest-replies-section
       authors-string
       " "
       replies-string
       " "
       [:span.underline
        replies-word]
       " "
       updates-string
       " "
       care-string])))

(defn new-boards-summary-node
  "Give the newly created boards list, creates a phrase like:
   - Since your last digest, 1 topic was created: New topic.
   - Since your last digest, 2 topics were created: First topic and Second topic.
   - Since your last digest, 3 topics were created: First topic, Second topic and Third topic.
   - Since your last digest, 4 topics were created: First topic, Second topic, Third topic and Fourth topic."
  [new-boards-list board-url-fn]
  (let [board-count (count new-boards-list)]
    (when-not (zero? board-count)
      (case board-count
       1 [:p.digest-new-boards-section
           "Since your last digest, "
           [:a
             {:href (board-url-fn "topics")}
             "1 topic "]
           "was created: "
           [:a
             {:href (board-url-fn (-> new-boards-list first :slug))}
             (-> new-boards-list first :name)]
          "."]
        2 [:p.digest-new-boards-section
            "Since your last digest, "
            [:a
              {:href (board-url-fn "topics")}
              board-count " topics "]
            "were created: "
            [:a
              {:href (board-url-fn (-> new-boards-list first :slug))}
              (-> new-boards-list first :name)]
            " and "
            [:a
              {:href (board-url-fn (-> new-boards-list second :slug))}
              (-> new-boards-list second :name)]
            "."]
          [:p.digest-new-boards-section
            "Since your last digest, "
            [:a
              {:href (board-url-fn "topics")}
              board-count " topics "]
            "were created: "
            (for [b (subvec new-boards-list 0 (- board-count 2))]
              [:span
                "&nbsp;"
                [:a
                  {:href (board-url-fn (:slug b))}
                  (:name b)]
                ","])
            [:a
              {:href (board-url-fn (-> new-boards-list butlast last :slug))}
              (-> new-boards-list butlast last :name)]
            " and "
            [:a
              {:href (board-url-fn (-> new-boards-list last :slug))}
              (-> new-boards-list last :name)]
            "."]))))


(def strip-xss-tags
  "This is needed for backward compatibility."
  oc-html/strip-xss-tags)