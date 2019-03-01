(ns oc.lib.text
  "Functions related to processing text."
  (:require [clojure.string :as s]
            [cuerdas.core :as str]
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


(defn strip-xss-tags
  "
   Current xss tags are script, style, and input.
  "
  [data]
  (when data (s/replace data #"(?i)<\/?((script|style|input){1})(\s?[^<>]*)>" "")))

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

(defn alt-attribution
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
                              (let [other-count (- (count distinct-authors)
                                                   attribution-count)]
                                (str (clojure.string/join ", " author-names)
                                     " and "
                                     other-count
                                     " other"
                                     (when (and (zero? other-count)
                                                (> 1 other-count))
                                       "s")))

                              ;; more than 1 author so last mention needs an "and", not a comma
                              multiple-authors?
                              (str (clojure.string/join ", " (butlast author-names))
                                                        " and "
                                                        (last author-names))

                              ;; just 1 author
                              :else
                              (first author-names))]
    (str item-count " " item-name (when (> item-count 1) "s") " by " author-attribution)))

(defn comments-reactions-attribution [comment-authors comment-count reaction-data receiver]
  (let [comments (attribution 3 comment-count "comment" comment-authors)
        reaction-authors (map #(hash-map :name %)
                              (flatten (map :authors reaction-data)))
        reaction-author-ids (or (flatten (map :author-ids reaction-data)) [])
        reaction-authors-you (if (some #(= % (:user-id receiver))
                                       reaction-author-ids)
                               (map #(if (= (:name receiver) (:name %))
                                       (assoc % :name "you")
                                       %)
                                    reaction-authors)
                               reaction-authors)
        comment-authors-you (map #(when (= (:user-id receiver) (:user-id %))
                                    (assoc % :name "you"))
                                 comment-authors)
        comment-authors-name (map #(hash-map :name (:name %))
                                  comment-authors-you)
        total-authors (vec (set
                            (concat reaction-authors-you
                                    comment-authors-name)))
        total-authors-sorted (remove #(nil? (:name %))
                               (conj (remove #(= (:name %) "you")
                                             total-authors)
                                     (first (filter #(= (:name %) "you")
                                                    total-authors))))
        reactions (attribution 3
                               (count reaction-data)
                               "reaction"
                               reaction-authors)
        total-attribution (alt-attribution 2
                                            (+ (count reaction-data)
                                               comment-count)
                                            "comments/reactions"
                                            total-authors-sorted)
        comment-text (clojure.string/join " "
                      (take 2 (clojure.string/split comments #" ")))
        reaction-text (clojure.string/join " "
                       (take 2 (clojure.string/split reactions #" ")))
        author-text (clojure.string/join " "
                      (subvec
                       (clojure.string/split total-attribution #" ") 2))]
    (cond 
      ;; Comments and reactions
      (and (pos? comment-count) (pos? (or (count reaction-data) 0)))
      (str comment-text " and " reaction-text " " author-text)
      ;; Comments only
      (pos? comment-count)
      (str comment-text " " author-text)
      ;; Reactions only
      :else
      (str reaction-text " " author-text))))