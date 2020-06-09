(ns oc.lib.change.resources.read
  "Retrieve tuples from read table of Change service"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [taoensso.timbre :as timbre]
            [oc.lib.schema :as lib-schema]))

(defn table-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_read")))

(defn user-id-gsi-name [db-opts]
  (str (:table-prefix db-opts) "_read_gsi_user_id"))

(defn org-id-user-id-gsi-name [db-opts]
  (str (:table-prefix db-opts) "_read_gsi_org_id_user_id"))

(defn container-id-item-id-gsi-name [db-opts]
  (str (:table-prefix db-opts) "_read_gsi_container_id_item_id"))

(defn container-id-gsi-name [db-opts]
  (str (:table-prefix db-opts) "_read_gsi_container_id"))

;; In theory, DynamoDB (and by extension, Faraday) support `{:return :count}` but it doesn't seem to be working
;; https://github.com/ptaoussanis/faraday/issues/91
(defn- count-for [db-opts user-id item-id]
  (let [results (far/query db-opts (table-name db-opts) {:item_id [:eq item-id]})]
    {:item-id item-id :count (count results) :last-read-at (:read_at (last (sort-by :read-at (filterv #(= (:user_id %) user-id) results))))}))

(schema/defn ^:always-validate store!

  ;; Store a read entry for the specified user
  ([db-opts
    org-id :-  lib-schema/UniqueID
    container-id :- lib-schema/UniqueID
    item-id :- lib-schema/UniqueID
    user-id :- lib-schema/UniqueID
    user-name :- schema/Str
    avatar-url :- (schema/maybe schema/Str)
    read-at :- lib-schema/ISO8601]
  (far/put-item db-opts (table-name db-opts) {
      :org-id org-id
      :container_id container-id
      :item_id item-id
      :user_id user-id
      :name user-name
      :avatar_url avatar-url
      :read_at read-at})
  true))

(schema/defn ^:always-validate delete!
  [db-opts item-id :- lib-schema/UniqueID user-id :- lib-schema/UniqueID]
  (far/delete-item db-opts (table-name db-opts) {:item_id item-id
                                                 :user_id user-id}))

(schema/defn ^:always-validate delete-by-item!
  [db-opts container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (doseq [item (far/query db-opts (table-name db-opts) {:item_id [:eq item-id]} {:index (container-id-item-id-gsi-name db-opts)})]
    (far/delete-item db-opts (table-name db-opts) {:item_id (:item_id item)
                                                   :user_id (:user_id item)})))

(schema/defn ^:always-validate delete-by-container!
  [db-opts container-id :- lib-schema/UniqueID]
  (doseq [item (far/query db-opts (table-name db-opts) {:container_id [:eq container-id]} {:index (container-id-gsi-name db-opts)})]
    (far/delete-item db-opts (table-name db-opts) {:item_id (:item_id item)
                                                   :user_id (:user_id item)})))

(schema/defn ^:always-validate move-item!
  [db-opts item-id :- lib-schema/UniqueID old-container-id :- lib-schema/UniqueID new-container-id :- lib-schema/UniqueID]
  (let [items-to-move (far/query db-opts (table-name db-opts) {:item_id [:eq item-id]}
                                                              {:index (container-id-item-id-gsi-name db-opts)})]
    (timbre/info "Read move-item! for" item-id "moving:" (count items-to-move) "items from container" old-container-id "to" new-container-id)
    (doseq [item items-to-move]
      (let [full-item (far/get-item db-opts (table-name db-opts) {:item_id (:item_id item) :user_id (:user_id item)})]
        (far/delete-item db-opts (table-name db-opts) {:item_id (:item_id full-item)
                                                       :user_id (:user_id full-item)})
        (far/put-item db-opts (table-name db-opts) {
          :org-id (:org-id full-item)
          :container_id new-container-id
          :item_id (:item_id full-item)
          :user_id (:user_id full-item)
          :name (:name full-item)
          :avatar_url (:avatar_url full-item)
          :read_at (:read_at full-item)})))))

(schema/defn ^:always-validate retrieve-by-item :- [{:user-id lib-schema/UniqueID
                                                     :name schema/Str
                                                     :avatar-url (schema/maybe schema/Str)
                                                     :read-at lib-schema/ISO8601}]
  [db-opts item-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (table-name db-opts) {:item_id [:eq item-id]})
      (map #(clojure.set/rename-keys % {:user_id :user-id :avatar_url :avatar-url :read_at :read-at}))
      (map #(select-keys % [:user-id :name :avatar-url :read-at]))))

(schema/defn ^:always-validate retrieve-by-user :- [{(schema/optional-key :container-id) lib-schema/UniqueID
                                                     :item-id lib-schema/UniqueID
                                                     :read-at lib-schema/ISO8601}]
  ([db-opts user-id :- lib-schema/UniqueID]
  (->>
      (far/query db-opts (table-name db-opts) {:user_id [:eq user-id]} {:index (user-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:container_id :container-id :item_id :item-id :read_at :read-at}))
      (map #(select-keys % [:container-id :item-id :read-at]))))

  ([db-opts user-id :- lib-schema/UniqueID container-id :- lib-schema/UniqueID]
  (->>
      (far/query db-opts (table-name db-opts) {:user_id [:eq user-id] :container_id [:eq container-id]}
                                              {:index (user-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:item_id :item-id :read_at :read-at}))
      (map #(select-keys % [:item-id :read-at])))))

(schema/defn ^:always-validate retrieve-by-user-item :- {(schema/optional-key :user-id) lib-schema/UniqueID
                                                         (schema/optional-key :name) schema/Str
                                                         (schema/optional-key :avatar-url) (schema/maybe schema/Str)
                                                         (schema/optional-key :read-at) lib-schema/ISO8601}
  [db-opts user-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (-> (far/get-item db-opts (table-name db-opts) {:item_id item-id
                                                               :user_id user-id})
      (clojure.set/rename-keys {:user_id :user-id :avatar_url :avatar-url :read_at :read-at})
      (select-keys [:user-id :name :avatar-url :read-at])))

(schema/defn ^:always-validate retrieve-by-user-org :- [{(schema/optional-key :item-id) lib-schema/UniqueID
                                                         (schema/optional-key :read-at) lib-schema/ISO8601}]
  [db-opts org-id :- lib-schema/UniqueID user-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (table-name db-opts) {:org-id [:eq org-id]
                                                            :user_id [:eq user-id]}
                                                           {:index (org-id-user-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:item_id :item-id :read_at :read-at}))
      (map #(select-keys % [:item-id :read-at]))))

(schema/defn ^:always-validate counts :- [{:item-id lib-schema/UniqueID
                                           :count schema/Int
                                           :last-read-at (schema/maybe lib-schema/ISO8601)}]
  [db-opts item-ids :- [lib-schema/UniqueID] user-id :- lib-schema/UniqueID]
  (pmap (partial count-for db-opts user-id) item-ids))