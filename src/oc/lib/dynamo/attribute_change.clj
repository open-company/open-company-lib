(ns oc.lib.dynamo.attribute-change
  (:require [taoensso.faraday :as far]
            [cuerdas.core :as string]
            [taoensso.timbre :as timbre]))

(defn- find-key [prim-keys find-key-type]
  (some (fn [[k v]] (when (= (:key-type v) find-key-type) (name k)))
        prim-keys))

(defn- find-keys [dopts table-name]
  (let [table-description (far/describe-table dopts table-name)
        prim-keys (:prim-keys table-description)
        hash-key (find-key prim-keys :hash)
        range-key (find-key prim-keys :range)]
    (timbre/tracef "%s table primary keys: %s" table-name prim-keys)
    {:hash-key hash-key
     :range-key range-key}))

(defn attr-not-exists-filter-expr
  ([filter-attr hash-key range-key limit] (attr-not-exists-filter-expr filter-attr hash-key range-key limit true))
  ([filter-attr hash-key range-key limit limit-projection?]
   (timbre/debugf "Creating attribute doesn't exists filter expression for attribute %s, with hash key %s and range key %s, limit %d%s"
                  filter-attr hash-key range-key limit (when limit-projection? " (limit projection to primary keys)"))
   (let [proj-expr (when limit-projection?
                     {:proj-expr "#pk1, #pk2"
                      :expr-attr-names {"#pk1" hash-key
                                        "#pk2" range-key}})
         merged-expr (merge-with merge
                                 {:filter-expr "attribute_not_exists(#fk)"
                                  :limit limit
                                  :expr-attr-names {"#fk" filter-attr}}
                                 proj-expr)]
     (timbre/debugf "Not existing expression: %s" merged-expr)
     merged-expr)))

(defn attr-exists-filter-expr
  [filter-attr hash-key range-key limit limit-projection?]
  (let [proj-expr (when limit-projection?
                    {:proj-expr "#pk1, #pk2"
                     :expr-attr-names {"#pk1" hash-key
                                       "#pk2" range-key}})
        limit-expr (when (pos? limit)
                     {:limit limit})
        merged-expr (merge-with merge
                                {:filter-expr "attribute_exists(#fk)"
                                 :expr-attr-names {"#fk" filter-attr}}
                                limit-expr
                                proj-expr)]
    (timbre/debugf "Attribute %s existing expression: %s" filter-attr merged-expr)
    merged-expr))

(defn table-scan-for-non-existing-attribute
  ([dynamodb-opts table-name filter-attr hash-key range-key] (table-scan-for-non-existing-attribute dynamodb-opts table-name filter-attr hash-key range-key true))
  ([dynamodb-opts table-name filter-attr hash-key range-key limit-projection?]
   (far/scan dynamodb-opts table-name
             (attr-not-exists-filter-expr filter-attr hash-key range-key limit-projection?))))

(defn table-scan-for-existing-attribute
  ([dynamodb-opts table-name filter-attr hash-key range-key limit] (table-scan-for-existing-attribute dynamodb-opts table-name filter-attr hash-key range-key limit false))
  ([dynamodb-opts table-name filter-attr hash-key range-key limit limit-projection?]
   (far/scan dynamodb-opts table-name (attr-exists-filter-expr filter-attr hash-key range-key limit limit-projection?))))

(defn- update-cond-expr [item in-attr out-attr]
  (let [set? (string/empty-or-nil? (get item (keyword out-attr)))
        update-expr (if set?
                      "REMOVE #kd SET #ka = :va"
                      "REMOVE #kd")
        expr-attr-names (if set?
                          {"#kd" in-attr
                           "#ka" out-attr}
                          {"#kd" in-attr})
        expr-map-base {:update-expr update-expr
                       :expr-attr-names expr-attr-names
                       :return :updated-new}
        expr-map (if set?
                   (merge expr-map-base {:expr-attr-vals {":va" (get item (keyword in-attr))}})
                   expr-map-base)]
    (timbre/tracef "%s attribute already preset in record? %s" out-attr set?)
    (timbre/tracef "Update condition expression %s" expr-map)
    expr-map))

(defn- move-and-update-cond-expr [item in-attr out-attr move-values-map]
  (let [old-value (get item (keyword out-attr))
        new-value (get move-values-map old-value)
        update-expr "REMOVE #kd SET #ka = :va"
        expr-attr-names {"#kd" in-attr
                         "#ka" out-attr}
        expr-map {:update-expr update-expr
                  :expr-attr-names expr-attr-names
                  :expr-attr-vals {":va" (if new-value new-value old-value)}
                  :return :updated-new}]
    (timbre/tracef "{%s: %s} will be replaced with {%s: %s}" in-attr old-value out-attr new-value)
    (when-not new-value
      (timbre/warn "No value found for attribute %s and value %s" in-attr old-value))
    (timbre/tracef "Update condition expression %s" expr-map)
    expr-map))

(defn- item-sel [item hash-key range-key]
  (timbre/tracef "Updating item %s" item)
  (let [hash-key-kw (keyword hash-key)
        range-key-kw (keyword range-key)
        item-selector {hash-key-kw (get item hash-key-kw)
                       range-key-kw (get item range-key-kw)}]
    (timbre/tracef "Update selector: %s" item-selector)
    item-selector))

(defn delete-attr-sel [item hash-key range-key]
  {(keyword hash-key) (get item (keyword hash-key))
   (keyword range-key) (get item (keyword range-key))})

(defn delete-attr-expr [item delete-attr]
  (timbre/debugf "Delete attribute expression for item %s and delete attribute %s" item delete-attr)
  (let [expr {:update-expr "REMOVE #k"
              :expr-attr-names {"#k" delete-attr}}]
    (timbre/tracef "Delete condition expression %s" expr)
    expr))

(defn update-item-delete-attr [dynamodb-opts table-name hash-key range-key delete-attr item]
  (let [del-sel (delete-attr-sel item hash-key range-key)
        del-cond (delete-attr-expr item delete-attr)]
    (timbre/tracef "Deleting attribute from item %s with selector %s and condition %s" item del-sel del-cond)
    (far/update-item dynamodb-opts table-name del-sel del-cond)))

(defn update-attribute-name
  ([dynamodb-opts table-name in-attr out-attr {limit :limit run? :run? :or {limit 100} :as params}]
   (timbre/infof "Replacing attribute %s with %s in %s table, params: %s" in-attr out-attr table-name params)
   (let [{:keys [hash-key range-key]} (find-keys dynamodb-opts table-name)
         all-in-items (table-scan-for-existing-attribute dynamodb-opts table-name in-attr hash-key range-key limit)]
     (timbre/infof "Loaded %d items" (count all-in-items))
     (timbre/tracef "Loaded items: %s" all-in-items)
     (let [updated-items (when run?
                           (timbre/tracef "Updating items...")
                           (mapv (fn [item]
                                   (far/update-item dynamodb-opts table-name (item-sel item hash-key range-key) (update-cond-expr item in-attr out-attr)))
                                 all-in-items))]
       (timbre/tracef "Updated items: %s" updated-items)
       (timbre/infof "Updated %d items" (count updated-items))
       updated-items))))

(defn move-attribute
  [dynamodb-opts table-name in-attr out-attr move-values-map {limit :limit run? :run? :or {limit 100} :as params}]
  (timbre/infof "Move attribute from %s to %s in %s table, params: %s" in-attr out-attr table-name params)
  (timbre/tracef "Move values map: %s" move-values-map)
   (let [{:keys [hash-key range-key]} (find-keys dynamodb-opts table-name)
         all-in-items (table-scan-for-existing-attribute dynamodb-opts table-name in-attr hash-key range-key limit)]
     (timbre/infof "Loaded %d items" (count all-in-items))
     (timbre/tracef "Loaded items: %s" all-in-items)
     (let [updated-items (when run?
                           (timbre/tracef "Updating items...")
                           (mapv (fn [item]
                                   (let [item-sel (item-sel item hash-key range-key)
                                         update-expr (move-and-update-cond-expr item in-attr out-attr move-values-map)]
                                     (far/update-item dynamodb-opts table-name item-sel update-expr)))
                                 all-in-items))]
       (timbre/tracef "Updated items: %s" updated-items)
       (timbre/infof "Updated %d items" (count updated-items))
       updated-items)))

(defn delete-attribute
  [dynamodb-opts table-name delete-attr {limit :limit run? :run? :as params}]
  (timbre/infof "Deleting attribute %s from %s with params %s" delete-attr table-name params)
  (let [{:keys [hash-key range-key]} (find-keys dynamodb-opts table-name)
        all-delete-items (table-scan-for-existing-attribute dynamodb-opts table-name delete-attr hash-key range-key limit false)]
    (timbre/infof "Loaded %d items" (count all-delete-items))
    (timbre/tracef "Loaded items: %s" all-delete-items)
    (let [deleted-items (when run?
                          (timbre/tracef "Deleting attribute items...")
                          (mapv (partial update-item-delete-attr dynamodb-opts table-name hash-key range-key delete-attr)
                                all-delete-items))]
      (timbre/tracef "Updated items: %s" deleted-items)
      (timbre/infof "Updated %d items" (count deleted-items))
      deleted-items)))

(defn table-has-attribute? [dynamodb-opts table-name attr]
  (timbre/infof "Lookup attribute %s for table-name %s" attr table-name)
  (let [table-keys (find-keys dynamodb-opts table-name)]
    (timbre/infof "Table keys: %s" table-keys)
    (some #(when (= (keyword attr) (keyword %)) %) (vals table-keys))))

(comment
  ;; Usage
  (require '[oc.change.utils.update-attribute-name :refer (update-attribute-name)])

  ;; Replace org-id with org_id in read table (limit to the first 100 records)
  (require '[oc.change.resources.read :as read])
  ;; Try a dry run first:
  (update-attribute-name read/table-name "org-id" "org_id" {:limit 100})
  ;; Apply the changes
  (update-attribute-name read/table-name "org-id" "org_id" {:limit 100 :run? true})

  ;; Replace org-id with org_id in read table (limit to the first 100 records)
  (require '[oc.change.resources.seen :as seen])
  ;; Try a dry run first:
  (update-attribute-name seen/table-name "user-id" "user_id" {:limit 100})
  ;; Apply the changes
  (update-attribute-name seen/table-name "user-id" "user_id" {:limit 100 :run? true}))