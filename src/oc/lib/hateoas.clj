(ns oc.lib.hateoas)

(def GET "GET")
(def POST "POST")
(def PUT "PUT")
(def PATCH "PATCH")
(def DELETE "DELETE")

(defn link-map [rel method url media-type & others]
  (let [link-map (apply array-map (flatten (into
          [:rel rel :method method :href url] others)))]
    (if media-type
      (assoc link-map :type media-type)
      link-map)))

(defn self-link [url media-type]
  (link-map "self" GET url media-type))

(defn update-link [url media-type]
  (link-map "update" PUT url media-type))

(defn partial-update-link [url media-type]
  (link-map "partial-update" PATCH url media-type))

(defn delete-link [url]
  (array-map :rel "delete" :method DELETE :href url))

(defn revision-link [url updated-at media-type]
  (assoc (link-map "revision" GET url media-type) :updated-at updated-at))