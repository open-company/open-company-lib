(ns oc.lib.hateoas
  "Namespace of helpers for creating HATEOAS links."
  (:require [defun.core :refer (defun)]
            [cuerdas.core :as string]))

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
  ([rel method url media-types] (link-map rel method url media-types {}))
  
  ([rel method url media-types others]
  {:pre [(string? rel)
         (http-methods method)
         (string? url)
         (media-types? media-types)
         (map? others)]}
  (let [link-map (merge {:rel rel :method method :href url} others)
        accept (:accept media-types)
        accept-link-map (if accept (assoc link-map :accept accept) link-map)
        content-type (:content-type media-types)]
    (if content-type
      (assoc accept-link-map :content-type content-type)
      accept-link-map))))

(defn self-link 
  "Link that points back to the resource itself."
  ([url media-type]
  (self-link url media-type {}))
  
  ([url media-type others]
  (link-map "self" GET url media-type others)))

(defn collection-link
  "Link that points to a collection (list) of items."
  ([url media-type]
  (collection-link url media-type {}))
  
  ([url media-type others]
  (link-map "collection" GET url media-type others)))

(defn item-link
  "Link that points to an individual item in a collection."
  ([url media-type]
  (item-link url media-type {}))
  
  ([url media-type others]
  (link-map "item" GET url media-type)))

(defn up-link 
  "Link that points to the parent collection that contains this item."
  ([url media-type]
  (up-link url media-type {}))
  
  ([url media-type others]
  (link-map "up" GET url media-type others)))

(defn add-link
  "Link to add an existing item to a collection."
  ([method url media-type]
  (add-link method url media-type {}))
  
  ([method url media-type others]
  (link-map "add" method url media-type others)))

(defn remove-link
  "Link to remove an item from a collection."
  ([url]
  (remove-link url {} {}))
  
  ([url media-type]
  (remove-link url media-type {}))
  
  ([url media-type others]
  (link-map "remove" DELETE url media-type others)))

(defn create-link 
  "Link to create a new resource."
  ([url media-type]
  (create-link url media-type {}))

  ([url media-type others]
  (link-map "create" POST url media-type others)))

(defn update-link
  "Link to replace an existing resource with new content."
  ([url media-types]
  (update-link url media-types {}))

  ([url media-types others]
  (link-map "update" PUT url media-types others)))

(defn partial-update-link
  "Link to update an existing resource with a fragment of content that's merged into the existing content."
  ([url media-types]
  (partial-update-link url media-types {}))
  
  ([url media-types others]
  (link-map "partial-update" PATCH url media-types others)))

(defn delete-link
  "Link to delete an existing resource."
  ([url]
  (delete-link url {}))
  ([url others]
  (link-map "delete" DELETE url {} others)))

(defn archive-link
  "Link to archive an existing resource."
  ([url]
  (archive-link url {}))
  ([url others]
  (link-map "archive" DELETE url {} others)))

;; Href formatter

(defn link-replace-href
  "Given a link with an :href and a :replace map,
   and given a replacements map, apply the replacements to the link."
  [link replacements]
  (when (and (map? link)
             (:href link))
    (update link :href
     #(reduce (fn [href [k v]]
                (string/replace href v (str (get replacements k))))
       %
       (:replace link)))))

;; Retrieve link: mostly used by the client

(defn- s-or-k?
  "Truthy if the provided value is a string or a keyword."
  [value]
  (or (string? value)
      (keyword? value)))

(defn- nil-or-map?
  [v]
  (or (nil? v)
      (map? v)))

(defn- check-params [link params]
  (or (nil? params)
      (every? #(or (= % :replace)
                   (= (get link %) (get params %)))
       (keys params))))

(defun link-for

  ([nil & rest]
   false)

  ([links :guard sequential? rels :guard sequential? methods :guard sequential?]
   (some (fn [rel] (link-for links rel methods)) rels))

  ([links :guard sequential? rels :guard sequential? methods :guard sequential? params :guard nil-or-map? replacements :guard map?]
   (let [link (link-for links rels methods params)]
     (link-replace-href link replacements)))

  ([links :guard sequential? rels :guard sequential? methods :guard sequential? params :guard nil-or-map?]
   (some (fn [rel] (link-for links rel methods params)) rels))

  ([links :guard sequential? rels :guard sequential? method :guard string?]
   (some #(link-for links % method) rels))

  ([links :guard sequential? rels :guard sequential? method :guard string? params :guard nil-or-map? replacements :guard map?]
   (let [link (link-for links rels method params)]
     (link-replace-href link replacements)))

  ([links :guard sequential? rels :guard sequential? method :guard string? params :guard nil-or-map?]
   (some #(link-for links % method params) rels))

  ([links :guard sequential? rel :guard string? methods :guard sequential?]
   (some #(link-for links rel %) methods))

  ([links :guard sequential? rel :guard string? methods :guard sequential? params :guard nil-or-map? replacements :guard map?]
   (let [link (link-for links rel methods params)]
     (link-replace-href link replacements)))

  ([links :guard sequential? rel :guard string? methods :guard sequential? params :guard nil-or-map?]
   (some #(link-for links rel % params) methods))

  ([links :guard sequential? rel :guard string?]
   (some #(when (= (:rel %) rel) %) links))

  ([links :guard sequential? rel :guard string? params :guard nil-or-map? replacements :guard map?]
   (let [link (link-for links rel params)]
     (link-replace-href link replacements)))

  ([links :guard sequential? rel :guard string? params :guard nil-or-map?]
   (some (fn [link]
           (when (and (= (:rel link) rel)
                      (check-params link params))
             link))
    links))

  ([links :guard sequential? rel :guard string? method :guard string?]
   (some (fn [link]
           (when (and (= (:rel link) rel)
                     (= (:method link) method))
             link))
    links))

  ([links :guard sequential? rel :guard string? method :guard string? params :guard nil-or-map? replacements :guard map?]
   (let [link (link-for links rel method params)]
     (link-replace-href link replacements)))

  ([links :guard sequential? rel :guard string? method :guard string? params :guard nil-or-map?]
   (some (fn [link]
           (when (and (= (:rel link) rel)
                      (= (:method link) method)
                      (check-params link params))
             link))
    links)))