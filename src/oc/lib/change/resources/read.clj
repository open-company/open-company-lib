(ns oc.lib.change.resources.read
  "Retrieve tuples from read table of Change service"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]))

(defn table-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_read")))

(defn- user-id-gsi-name [db-opts]
  (str (:table-prefix db-opts) "_read_gsi_user_id"))

(defn- org-id-user-id-gsi-name [db-opts]
  (str (:table-prefix db-opts) "_read_gsi_org_id_user_id"))

(schema/defn ^:always-validate retrieve-by-user :- [{(schema/optional-key :container-id) lib-schema/UniqueID
                                                     :item-id lib-schema/UniqueID
                                                     :read-at lib-schema/ISO8601}]
  [dynamodb-opts user-id :- lib-schema/UniqueID]
  (->>
      (far/query dynamodb-opts (table-name dynamodb-opts) {:user_id [:eq user-id]}
       {:index (user-id-gsi-name dynamodb-opts)})
      (map #(clojure.set/rename-keys % {:container_id :container-id :item_id :item-id :read_at :read-at}))
      (map #(select-keys % [:container-id :item-id :read-at]))))

(schema/defn ^:always-validate retrieve-by-user-item :- {(schema/optional-key :user-id) lib-schema/UniqueID
                                                         (schema/optional-key :name) schema/Str
                                                         (schema/optional-key :avatar-url) (schema/maybe schema/Str)
                                                         (schema/optional-key :read-at) lib-schema/ISO8601}
  [dynamodb-opts user-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (-> (far/get-item dynamodb-opts (table-name dynamodb-opts) {:item_id item-id
                                                               :user_id user-id})
      (clojure.set/rename-keys {:user_id :user-id :avatar_url :avatar-url :read_at :read-at})
      (select-keys [:user-id :name :avatar-url :read-at])))

(schema/defn ^:always-validate retrieve-by-item :- [{:user-id lib-schema/UniqueID
                                                     :name schema/Str
                                                     :avatar-url (schema/maybe schema/Str)
                                                     :read-at lib-schema/ISO8601}]
  [dynamodb-opts item-id :- lib-schema/UniqueID]
  (->> (far/query dynamodb-opts (table-name dynamodb-opts) {:item_id [:eq item-id]})
      (map #(clojure.set/rename-keys % {:user_id :user-id :avatar_url :avatar-url :read_at :read-at}))
      (map #(select-keys % [:user-id :name :avatar-url :read-at]))))

(schema/defn ^:always-validate retrieve-by-user-org :- [{(schema/optional-key :item-id) lib-schema/UniqueID
                                                         (schema/optional-key :read-at) lib-schema/ISO8601}]
  [dynamodb-opts org-id :- lib-schema/UniqueID user-id :- lib-schema/UniqueID]
  (->> (far/query dynamodb-opts (table-name dynamodb-opts) {:org-id [:eq org-id]
                                                            :user_id [:eq user-id]}
                                                           {:index (org-id-user-id-gsi-name dynamodb-opts)})
      (map #(clojure.set/rename-keys % {:item_id :item-id :read_at :read-at}))
      (map #(select-keys % [:item-id :read-at]))))
