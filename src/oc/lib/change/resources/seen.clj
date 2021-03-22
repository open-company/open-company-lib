(ns oc.lib.change.resources.seen
  "Store tuples of: user-id, container-id, item-id and timestamp, with a TTL"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [taoensso.timbre :as timbre]
            [clojure.set :as clj-set]
            [oc.lib.dynamo.ttl :as ttl]))

(def entire-container "9999-9999-9999")

(defn table-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_seen")))

(def container-id-item-id-gsi-projection :all)

(defn container-id-item-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_seen_gsi_container_id_item_id")))

(def container-id-gsi-projection :all)

(defn container-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_seen_gsi_container_id")))

(defn org-id-user-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_seen_gsi_org_id_user_id")))

(defn- create-container-item-id [container-id item-id]
  (str container-id "-" item-id))

(declare retrieve-by-container-item)
(declare store!)

(schema/defn ^:always-validate delete-by-item!
  [db-opts container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (doseq [item (far/query db-opts (table-name db-opts) {:container_id [:eq container-id]
                                                        :item_id [:eq item-id]}
                                                       {:index (container-id-item-id-gsi-name db-opts)})]
    (far/delete-item db-opts (table-name db-opts) {:container_item_id (:container_item_id item)
                                                   :user_id (:user_id item)})))

(schema/defn ^:always-validate delete-by-container!
  [db-opts container-id :- lib-schema/UniqueID]
  (doseq [item (far/query db-opts (table-name db-opts) {:container_id [:eq container-id]} {:index (container-id-gsi-name db-opts)})]
    (far/delete-item db-opts (table-name db-opts) {:container_item_id (:container_item_id item)
                                                   :user_id (:user_id item)})))

(schema/defn ^:always-validate move-item!
  [db-opts item-id :- lib-schema/UniqueID old-container-id :- lib-schema/UniqueID new-container-id :- lib-schema/UniqueID]
  (let [items-to-move (retrieve-by-container-item db-opts old-container-id item-id)]
    (timbre/info "Seen move-item! for" item-id "moving:" (count items-to-move) "items from container" old-container-id "to" new-container-id)
    (doseq [item items-to-move]
      (store! db-opts (assoc item :container-id new-container-id))
      (delete-by-item! db-opts old-container-id item-id))
    true))

(schema/defn ^:always-validate retrieve :- [{:container-id lib-schema/UniqueID
                                             :item-id lib-schema/UniqueID
                                             :seen-at lib-schema/ISO8601}]
  [db-opts user-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (table-name db-opts) {:user_id [:eq user-id]}
        {:filter-expr "#k > :v"
         :expr-attr-names {"#k" "ttl"}
         :expr-attr-vals {":v" (ttl/ttl-now)}})
      (map #(clj-set/rename-keys % {:container_id :container-id :item_id :item-id :seen_at :seen-at}))
      (map #(select-keys % [:container-id :item-id :seen-at]))))

(schema/defn ^:always-validate retrieve-by-container-item :- [{(schema/optional-key :org-id) lib-schema/UniqueID
                                                               (schema/optional-key :container-id) lib-schema/UniqueID
                                                               (schema/optional-key :item-id) lib-schema/UniqueID
                                                               (schema/optional-key :container-item-id) lib-schema/DoubleUniqueID
                                                               (schema/optional-key :user-id) lib-schema/UniqueID
                                                               (schema/optional-key :seen-at) lib-schema/ISO8601
                                                               (schema/optional-key :seen-ttl) schema/Any}]
  [db-opts container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (table-name db-opts) {:container_id [:eq container-id]
                                                :item_id [:eq item-id]}
                  {:index (container-id-item-id-gsi-name db-opts)})
       (map #(clj-set/rename-keys % {:org_id :org-id
                                         :container_id :container-id
                                         :item_id :item-id
                                         :container_item_id :container-item-id
                                         :user_id :user-id
                                         :seen_at :seen-at
                                         :seen_ttl :seen-ttl}))
       (map #(select-keys % [:org-id :container-id :item-id :container-item-id :user-id :seen-at :seen-ttl]))))

(schema/defn ^:always-validate retrieve-by-user-org :- [{(schema/optional-key :container-id) lib-schema/UniqueID
                                                         (schema/optional-key :item-id) lib-schema/UniqueID
                                                         (schema/optional-key :seen-at) lib-schema/ISO8601}]
  [db-opts user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (table-name db-opts) {:org_id [:eq org-id]
                                                :user_id [:eq user-id]}
                                               {:index (org-id-user-id-gsi-name db-opts)})
      (map #(clj-set/rename-keys % {:container_id :container-id :item_id :item-id :seen_at :seen-at}))
      (map #(select-keys % [:item-id :container-id :seen-at]))))

(schema/defn ^:always-validate retrieve-by-user-container :- {(schema/optional-key :org-id) lib-schema/UniqueID
                                                              (schema/optional-key :container-id) lib-schema/UniqueID
                                                              (schema/optional-key :seen-at) lib-schema/ISO8601}
  [db-opts user-id :- lib-schema/UniqueID container-id :- lib-schema/UniqueID]
  (let [seen-items (far/query db-opts (table-name db-opts) {:container_id [:eq container-id]
                                                            :user_id [:eq user-id]}
                                                           {:index (container-id-gsi-name db-opts)})]
    (if (seq seen-items)
      (-> (first seen-items)
       (clj-set/rename-keys {:container_id :container-id :org_id :org-id :seen_at :seen-at})
       (select-keys [:org-id :container-id :seen-at]))
      {})))

(schema/defn ^:always-validate retrieve-by-user-item :- (schema/maybe {:org-id lib-schema/UniqueID
                                                                       :container-id lib-schema/UniqueID
                                                                       :item-id lib-schema/UniqueID
                                                                       :seen-at lib-schema/ISO8601})
  [db-opts user-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (let [items (far/query db-opts (table-name db-opts) {:user_id [:eq user-id]}
                                                     {:filter-expr "#k = :v"
                                                      :expr-attr-names {"#k" "item_id"}
                                                      :expr-attr-vals {":v" item-id}})]
    (when-let [item (first items)]
      (-> item
       (clj-set/rename-keys {:org_id :org-id :container_id :container-id :item_id :item-id :seen_at :seen-at})
       (select-keys [:org-id :container-id :item-id :seen-at])))))

(schema/defn ^:always-validate store!
  ;; Clone a seen item, NB: it uses dashed keys, not underscores
  ([db-opts seen-item]
   (store! db-opts (:user-id seen-item) (:org-id seen-item) (:container-id seen-item) (:item-id seen-item)
           (:seen-at seen-item) (:seen-ttl seen-item)))

  ;; Saw the whole container, so the item-id is a placeholder
  ([db-opts
    user-id :- lib-schema/UniqueID
    org-id :- lib-schema/UniqueID
    container-id :- lib-schema/UniqueID
    seen-at :- lib-schema/ISO8601
    seen-ttl :- schema/Int]
  (store! db-opts user-id org-id container-id entire-container seen-at seen-ttl))

  ;; Store a seen entry for the specified user
  ([db-opts
    user-id :- lib-schema/UniqueID
    org-id :- lib-schema/UniqueID
    container-id :- lib-schema/UniqueID
    item-id :- lib-schema/UniqueID
    seen-at :- lib-schema/ISO8601
    seen-ttl :- schema/Int]
  (let [prev-seen (retrieve-by-user-item db-opts user-id item-id)]
    (when (pos? (compare seen-at (:seen-at prev-seen)))
      (far/put-item db-opts (table-name db-opts) {
          :user_id user-id
          :org_id org-id
          :container_item_id (create-container-item-id container-id item-id)
          :container_id container-id
          :item_id item-id
          :seen_at seen-at
          :ttl (ttl/ttl-epoch seen-ttl)})))
  true))

(comment

  (require '[oc.lib.time :as oc-time])
  (require '[oc.change.resources.seen :as seen] :reload)

  (far/list-tables db-opts)

  (far/delete-table db-opts (lib-seen/table-name db-opts))
  (aprint
    (far/create-table db-opts
      (lib-seen/table-name db-opts)
      [:user_id :s]
      {:range-keydef [:container_item_id :s]
       :billing-mode :pay-per-request
       :block? true}))
  ;; GSI used for delete via item-id
  (aprint
    @(far/update-table config/dynamodb-opts
      (lib-seen/table-name db-opts)
      {:gsindexes {:operation :create
                   :name seen/container-id-item-id-gsi-name
                   :billing-mode :pay-per-request
                   :hash-keydef [:item_id :s]
                   :range-keydef [:container_id :s]
                   :projection :all}}))

  (doseq [item (far/query config/dynamodb-opts (lib-seen/table-name db-opts) {:container_id [:eq "25a3-4692-bf02"]
                                                                              :item_id [:eq "512b-4ad1-9924"]}
                                                                             {:index seen/container-id-item-id-gsi-name})]
    (aprint
      (far/delete-item config/dynamodb-opts (lib-seen/table-name db-opts) {:container_item_id (:container_item_id item)
                                                             :user_id (:user_id item)})))

  ;; GSI used for delete via container-id
  (aprint
    @(far/update-table config/dynamodb-opts
      (lib-seen/table-name  db-opts)
      {:gsindexes {:operation :create
                   :name seen/container-id-gsi-name
                   :billing-mode :pay-per-request
                   :hash-keydef [:container_id :s]
                   :range-keydef [:user_id :s]
                   :projection :all}}))

  (doseq [item (far/query config/dynamodb-opts (lib-seen/table-name db-opts) {:container_id [:eq "25a3-4692-bf02"]} {:index seen/container-id-gsi-name})]
    (aprint
      (far/delete-item config/dynamodb-opts (lib-seen/table-name db-opts) {:container_item_id (:container_item_id item)
                                                             :user_id (:user_id item)})))

  (far/delete-item config/dynamodb-opts (lib-seen/table-name db-opts) {:container_item_id "25a3-4692-bf02-512b-4ad1-9924"})

  (aprint (far/describe-table config/dynamodb-opts (lib-seen/table-name db-opts)))

  (lib-seen/store! db-opts "abcd-1234-abcd" "5678-edcb-5678" (oc-time/current-timestamp))

  (lib-seen/retrieve db-opts "abcd-1234-abcd")

  (lib-seen/store! db-opts "abcd-1234-abcd" "1ab1-2ab2-3ab3" "1111-1111-1111" (oc-time/current-timestamp))

  (lib-seen/retrieve db-opts "abcd-1234-abcd")

  (far/delete-table db-opts (lib-seen/table-name db-opts))
)