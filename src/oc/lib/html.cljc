(ns oc.lib.html
  "Functions related to processing HTML."
  (:require [cuerdas.core :as str]
            #?(:clj [jsoup.soup :as soup])))

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