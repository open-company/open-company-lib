(ns oc.lib.user
  (:require [clojure.string :as s]
            [defun.core :refer (defun)]))

(def author-logo 32)

(defn- circle-image
  "Return an on the fly url of the image circle and resized."
  [filestack-api-key image-url size]
  ;; Filestack URL https://cdn.filestackcontent.com/qemc9YslR9yabfqL4GTe
  (let [filestack-static-url "https://cdn.filestackcontent.com/"
        is-filestack-resource? (s/starts-with? image-url filestack-static-url)
        filestack-resource (if is-filestack-resource?
                             (subs image-url (count filestack-static-url))
                             image-url)]
    (str "https://process.filestackapi.com/"
         (when-not is-filestack-resource?
           (str filestack-api-key "/"))
         "resize=w:" author-logo ",h:" author-logo ",fit:crop,align:faces/"
         "circle/"
         filestack-resource)))

(defn fix-avatar-url
  "
  First it fixes relative URLs, it prepends our production CDN domain to it if it's relative.
  Then if the url is pointing to one of our happy faces, it replaces the SVG extension with PNG
  to have it resizable. If it's not one of our happy faces, it uses the on-the-fly resize url.
  "
  [filestack-api-key avatar-url]
  (let [absolute-avatar-url (if (s/starts-with? avatar-url "/img")
                              (str "https://d1wc0stj82keig.cloudfront.net" avatar-url)
                              avatar-url)]
    (if (re-seq #"happy_face_(red|green|blue|purple|yellow).svg$" absolute-avatar-url) ; carrot default?
      (str (subs absolute-avatar-url 0 (- (count absolute-avatar-url) 3)) "png")
      (circle-image filestack-api-key absolute-avatar-url 32))))

(defun name-for;
  "
  Make a single `name` field from `first-name` and/or `last-name`.

  Use email as the name if the entire user is provided and there's no first or last name.
  "
  ([user :guard #(and (s/blank? (:first-name %)) (s/blank? (:last-name %)))] (name-for (:email user) ""))
  ([user] (name-for (:first-name user) (:last-name user)))
  ([first-name :guard s/blank? last-name :guard s/blank?] "")
  ([first-name last-name :guard s/blank?] first-name)
  ([first-name :guard s/blank? last-name] last-name)
  ([first-name last-name] (str first-name " " last-name)))