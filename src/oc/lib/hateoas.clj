(ns oc.lib.hateoas
  "Namespace of helpers for creating HATEOAS links.")

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

(defn self-link 
  ([url media-type]
  (link-map "self" GET url media-type))
  ([url media-type & others]
  (link-map "self" GET url media-type others)))

(defn item-link
  ([url media-type]
  (link-map "item" GET url media-type))
  ([url media-type & others]
  (link-map "item" GET url media-type)))

(defn collection-link
  ([url media-type]
  (link-map "collection" GET url media-type))
  ([url media-type & others]
  (link-map "collection" GET url media-type others)))

(defn up-link 
  ([url media-type]
  (link-map "up" GET url media-type))
  ([url media-type & others]
  (link-map "up" GET url media-type others)))

(defn create-link 
  ([url media-type]
  (link-map "create" POST url media-type))
  ([url media-type & others]
  (link-map "create" POST url media-type others)))

(defn update-link
  ([url media-type]
  (link-map "update" PUT url media-type))
  ([url media-type & others]
  (link-map "update" PUT url media-type others)))

(defn partial-update-link
  ([url media-type]
  (link-map "partial-update" PATCH url media-type))
  ([url media-type & others]
  (link-map "partial-update" PATCH url media-type others)))

(defn delete-link [url]
  (array-map :rel "delete" :method DELETE :href url))

(defn revision-link [url updated-at media-type]
  (assoc (link-map "revision" GET url media-type) :updated-at updated-at))