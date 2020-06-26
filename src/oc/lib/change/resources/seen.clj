(ns oc.lib.change.resources.seen
  "Store tuples of: user-id, container-id, item-id and timestamp, with a TTL"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [taoensso.timbre :as timbre]
            [oc.lib.dynamo.common :as ttl]))

(def entire-container "9999-9999-9999")

(defn table-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_seen")))

(defn container-id-item-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_seen_gsi_container_id_item_id")))

(defn container-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_seen_gsi_container_id")))

(defn org-id-user-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_seen_gsi_org_id_user_id")))

(defn- create-container-item-id [container-id item-id]
  (str container-id "-" item-id))

(schema/defn ^:always-validate store!

  ;; Saw the whole container, so the item-id is a placeholder
  ([db-opts
    user-id :- lib-schema/UniqueID
    org-id :- lib-schema/UniqueID
    container-id :- lib-schema/UniqueID
    seen-at :- lib-schema/ISO8601
    seen-ttl :- schema/Int]
  (store! user-id org-id container-id entire-container seen-at seen-ttl))

  ;; Store a seen entry for the specified user
  ([db-opts
    user-id :- lib-schema/UniqueID
    org-id :- lib-schema/UniqueID
    container-id :- lib-schema/UniqueID
    item-id :- lib-schema/UniqueID
    seen-at :- lib-schema/ISO8601
    seen-ttl :- schema/Int]
  (far/put-item db-opts (table-name db-opts) {
      :user_id user-id
      :org_id org-id
      :container_item_id (create-container-item-id container-id item-id)
      :container_id container-id
      :item_id item-id
      :seen_at seen-at
      :ttl (ttl/ttl-epoch seen-ttl)})
  true))

(schema/defn ^:always-validate delete-by-item!
  [db-opts container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (doseq [item (far/query db-opts (table-name db-opts) {:item_id [:eq item-id]} {:index (container-id-item-id-gsi-name db-opts)})]
    (far/delete-item db-opts (table-name db-opts) {:container_item_id (:container_item_id item)
                                                   :user_id (:user_id item)})))

(schema/defn ^:always-validate delete-by-container!
  [db-opts container-id :- lib-schema/UniqueID]
  (doseq [item (far/query db-opts (table-name db-opts) {:container_id [:eq container-id]} {:index (container-id-gsi-name db-opts)})]
    (far/delete-item db-opts (table-name db-opts) {:container_item_id (:container_item_id item)
                                                   :user_id (:user_id item)})))

(schema/defn ^:always-validate move-item!
  [db-opts item-id :- lib-schema/UniqueID old-container-id :- lib-schema/UniqueID new-container-id :- lib-schema/UniqueID]
  (let [items-to-move (far/query db-opts (table-name db-opts) {:item_id [:eq item-id] :container_id [:eq old-container-id]}
                       {:index (container-id-item-id-gsi-name db-opts)})]
    (timbre/info "Seen move-item! for" item-id "moving:" (count items-to-move) "items from container" old-container-id "to" new-container-id)
    (doseq [item items-to-move]
      (let [old-container-item-id (create-container-item-id old-container-id item-id)
            new-container-item-id (create-container-item-id new-container-id item-id)
            full-item (far/get-item db-opts (table-name db-opts) {:user_id (:user_id item) :container_item_id old-container-item-id})]

        (far/delete-item db-opts (table-name db-opts) {:container_item_id (:container_item_id full-item)
                                                       :user_id (:user_id full-item)})
        (far/put-item db-opts (table-name db-opts) {
          :user_id (:user_id full-item)
          :org_id (:org_id full-item)
          :container_item_id new-container-item-id
          :container_id (:container_id full-item)
          :item_id (:item_id full-item)
          :seen_at (:seen_at full-item)
          :ttl (:ttl full-item)})))))

(schema/defn ^:always-validate retrieve :- [{:container-id lib-schema/UniqueID
                                             :item-id lib-schema/UniqueID
                                             :seen-at lib-schema/ISO8601}]
  [db-opts user-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (table-name db-opts) {:user_id [:eq user-id]}
        {:filter-expr "#k > :v"
         :expr-attr-names {"#k" "ttl"}
         :expr-attr-vals {":v" (ttl/ttl-now)}})
      (map #(clojure.set/rename-keys % {:container_id :container-id :item_id :item-id :seen_at :seen-at}))
      (map #(select-keys % [:container-id :item-id :seen-at]))))

(schema/defn ^:always-validate retrieve-by-user-org :- [{(schema/optional-key :container-id) lib-schema/UniqueID
                                                         (schema/optional-key :item-id) lib-schema/UniqueID
                                                         (schema/optional-key :seen-at) lib-schema/ISO8601}]
  [db-opts user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (table-name db-opts) {:org_id [:eq org-id]
                                                :user_id [:eq user-id]}
                                               {:index (org-id-user-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:container_id :container-id :item_id :item-id :seen_at :seen-at}))
      (map #(select-keys % [:item-id :container-id :seen-at]))))

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
       (clojure.set/rename-keys {:org_id :org-id :container_id :container-id :item_id :item-id :seen_at :seen-at})
       (select-keys [:org-id :container-id :item-id :seen-at])))))

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
                   :projection :keys-only}}))

  (doseq [item (far/query config/dynamodb-opts (lib-seen/table-name db-opts) {:item_id [:eq "512b-4ad1-9924"]} {:index seen/container-id-item-id-gsi-name})]
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
                   :projection :keys-only}}))

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