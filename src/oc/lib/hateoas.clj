(ns oc.lib.hateoas
  "Namespace of helpers for creating HATEOAS links.")

(def OPTIONS "OPTIONS")
(def HEAD "HEAD")
(def GET "GET")
(def POST "POST")
(def PUT "PUT")
(def PATCH "PATCH")
(def DELETE "DELETE")
(def http-methods #{OPTIONS HEAD GET POST PUT PATCH DELETE})

(def json-collection-version "1.0")

(defn- media-types?
  "Ensure media types is either a map with :accept and/or :content-type keys, or nothing"
  [media-types]
  (and (or (nil? media-types) (map? media-types))
       (clojure.set/subset? (keys media-types) #{:accept :content-type})))

(defn link-map
  "
  Create a HATEOAS link for the specified relation, HTTP method, URL, and media-type.

  Any additional key/values will be included as additional properties of the link.
  "
  [rel method url media-types & others]
  {:pre [(string? rel)
         (http-methods method)
         (string? url)
         (media-types? media-types)]}
  (let [link-map (apply array-map (flatten (into
                    [:rel rel :method method :href url] others)))
        accept (:accept media-types)
        accept-link-map (if accept (assoc link-map :accept accept) link-map)
        content-type (:content-type media-types)]
    (if content-type
      (assoc accept-link-map :content-type content-type)
      accept-link-map)))

(defn self-link 
  "Link that points back to the resource itself."
  ([url media-type]
  (link-map "self" GET url media-type))
  ([url media-type & others]
  (link-map "self" GET url media-type others)))

(defn collection-link
  "Link that points to a collection (list) of items."
  ([url media-type]
  (link-map "collection" GET url media-type))
  ([url media-type & others]
  (link-map "collection" GET url media-type others)))

(defn item-link
  "Link that points to an individual item in a collection."
  ([url media-type]
  (link-map "item" GET url media-type))
  ([url media-type & others]
  (link-map "item" GET url media-type)))

(defn up-link 
  "Link that points to the parent collection that contains this item."
  ([url media-type]
  (link-map "up" GET url media-type))
  ([url media-type & others]
  (link-map "up" GET url media-type others)))

(defn add-link
  "Link to add an existing item to a collection."
  ([method url media-type]
  (link-map "add" method url media-type))
  ([method url media-type & others]
  (link-map "add" method url media-type others)))

(defn remove-link
  "Link to remove an item from a collection."
  ([url]
  (link-map "remove" DELETE url nil))
  ([url media-type]
  (link-map "remove" DELETE url media-type))
  ([url media-type & others]
  (link-map "remove" DELETE url media-type others)))

(defn create-link 
  "Link to create a new resource."
  ([url media-type]
  (link-map "create" POST url media-type))
  ([url media-type & others]
  (link-map "create" POST url media-type others)))

(defn update-link
  "Link to replace an existing resource with new content."
  ([url media-types]
  (link-map "update" PUT url media-types))

  ([url media-types & others]
  (link-map "update" PUT url media-types others)))

(defn partial-update-link
  "Link to update an existing resource with a fragment of content that's merged into the existing content."
  ([url media-types]
  (link-map "partial-update" PATCH url media-types))
  
  ([url media-types & others]
  (link-map "partial-update" PATCH url media-types others)))

(defn delete-link
  "Link to delete an existing resource."
  ([url]
  (link-map "delete" DELETE url {}))
  ([url & others]
  (link-map "delete" DELETE url {} others)))