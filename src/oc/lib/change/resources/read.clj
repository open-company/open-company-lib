(ns oc.lib.change.resources.read
  "Retrieve tuples from read table of Change service"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [taoensso.timbre :as timbre]
            [oc.lib.schema :as lib-schema]))

;; Table and GSIndex names

(def ^:private table "_read")

(defn table-name [db-opts]
  (keyword (str (:table-prefix db-opts) table)))

(defn user-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) table "_gsi_user_id")))

(defn org-id-user-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) table "_gsi_org_id_user_id")))

(defn container-id-item-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) table "_gsi_container_id_item_id")))

(defn container-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) table "_gsi_container_id")))

;; Store

(schema/defn ^:always-validate store!

  ;; Store a read record for the specified user and item
  ([db-opts
    org-id :-  lib-schema/UniqueID
    container-id :- lib-schema/UniqueID
    item-id :- lib-schema/UniqueID
    user-id :- lib-schema/UniqueID
    user-name :- schema/Str
    avatar-url :- (schema/maybe schema/Str)
    read-at :- lib-schema/ISO8601]
  (far/put-item db-opts (table-name db-opts) {
      :org_id org-id
      :container_id container-id
      :item_id item-id
      :user_id user-id
      :name user-name
      :avatar_url avatar-url
      :read_at read-at})
  true))

;; Retrive

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
  [db-opts user-id :- lib-schema/UniqueID]
  (->>
      (far/query db-opts (table-name db-opts) {:user_id [:eq user-id]} {:index (user-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:container_id :container-id :item_id :item-id :read_at :read-at}))
      (map #(select-keys % [:container-id :item-id :read-at]))))

(schema/defn ^:always-validate retrieve-by-user-item :- {(schema/optional-key :org-id) lib-schema/UniqueID
                                                         (schema/optional-key :container-id) lib-schema/UniqueID
                                                         (schema/optional-key :item-id) lib-schema/UniqueID
                                                         (schema/optional-key :user-id) lib-schema/UniqueID
                                                         (schema/optional-key :name) schema/Str
                                                         (schema/optional-key :avatar-url) (schema/maybe schema/Str)
                                                         (schema/optional-key :read-at) lib-schema/ISO8601}
  [db-opts user-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (-> (far/get-item db-opts (table-name db-opts) {:item_id item-id
                                                  :user_id user-id})
      (clojure.set/rename-keys {:org_id :org-id :container_id :container-id :item_id :item-id :user_id :user-id :avatar_url :avatar-url :read_at :read-at})
      (select-keys [:org-id :container-id :item-id :user-id :name :avatar-url :read-at])))

(schema/defn ^:always-validate retrieve-by-user-container :- [{:item-id lib-schema/UniqueID
                                                               :read-at lib-schema/ISO8601}]
  [db-opts user-id :- lib-schema/UniqueID container-id :- lib-schema/UniqueID]
  (->>
      (far/query db-opts (table-name db-opts) {:user_id [:eq user-id] :container_id [:eq container-id]}
                                              {:index (user-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:item_id :item-id :read_at :read-at}))
      (map #(select-keys % [:item-id :read-at]))))

(schema/defn ^:always-validate retrieve-by-user-org :- [{(schema/optional-key :item-id) lib-schema/UniqueID
                                                         (schema/optional-key :container-id) lib-schema/UniqueID
                                                         (schema/optional-key :read-at) lib-schema/ISO8601}]
  [db-opts user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (table-name db-opts) {:org_id [:eq org-id] :user_id [:eq user-id]} {:index (org-id-user-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:container_id :container-id :item_id :item-id :read_at :read-at}))
      (map #(select-keys % [:item-id :container-id :read-at]))))

(schema/defn ^:always-validate retrieve-by-container :- [{(schema/optional-key :user-id) lib-schema/UniqueID
                                                          (schema/optional-key :item-id) lib-schema/UniqueID}]
  [db-opts container-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (table-name db-opts) {:container_id [:eq container-id]} {:index (container-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:user_id :user-id :item_id :item-id :read_at :read-at}))
      (map #(select-keys % [:user-id :item-id :read-at]))))

(schema/defn ^:always-validate retrieve-by-org :- [{(schema/optional-key :user-id) lib-schema/UniqueID
                                                    (schema/optional-key :item-id) lib-schema/UniqueID}]
  [db-opts org-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (table-name db-opts) {:org_id [:eq org-id]} {:index (org-id-user-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:user_id :user-id :item_id :item-id}))
      (map #(select-keys % [:user-id :item-id]))))

;; Move

(schema/defn ^:always-validate move-item!
  [db-opts item-id :- lib-schema/UniqueID old-container-id :- lib-schema/UniqueID new-container-id :- lib-schema/UniqueID]
  (let [items-to-move (retrieve-by-item db-opts item-id)]
    (timbre/info "Read move-item! for" item-id "moving:" (count items-to-move) "items from container" old-container-id "to" new-container-id)
    (doseq [item items-to-move]
      (far/update-item db-opts (table-name db-opts) {:item_id item-id
                                                     :user_id (:user-id item)}
                       {:update-expr "SET #k = :new_value"
                        :cond-expr "#k = :old_value"
                        :expr-attr-names {"#k" "container_id"}
                        :expr-attr-vals {":new_value" new-container-id
                                         ":old_value" old-container-id}
                        :return :all-new}))))

;; Delete

(schema/defn ^:always-validate delete-user-item!
  [db-opts user-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (far/delete-item db-opts (table-name db-opts) {:item_id item-id
                                                 :user_id user-id}))

(def delete! delete-user-item!)

(schema/defn ^:always-validate delete-by-item!

  ([db-opts container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
   (delete-by-item! db-opts item-id))

  ([db-opts item-id :- lib-schema/UniqueID]
   ;; Remove all item reads
   (doseq [item (retrieve-by-item db-opts item-id)]
     (delete! db-opts (:user-id item) item-id))))

(schema/defn ^:always-validate delete-by-container!
  [db-opts container-id :- lib-schema/UniqueID]
  ;; Remove all item reads
  (doseq [item (retrieve-by-container db-opts container-id)]
    (far/delete-item db-opts (table-name db-opts) {:item_id (:item-id item)
                                                   :user_id (:user-id item)})))

(schema/defn ^:always-validate delete-by-org!
  [db-opts org-id :- lib-schema/UniqueID]
  ;; Remove all item reads
  (doseq [item (retrieve-by-org db-opts org-id)]
    (far/delete-item db-opts (table-name db-opts) {:item_id (:item-id item)
                                                   :user_id (:user-id item)})))

;; Count

;; In theory, DynamoDB (and by extension, Faraday) support `{:return :count}` but it doesn't seem to be working
;; https://github.com/ptaoussanis/faraday/issues/91
(defn- count-for [db-opts user-id item-id]
  (let [item-reads (retrieve-by-item db-opts item-id)]
    {:item-id item-id
     :count (count item-reads)
     :last-read-at (->> item-reads
                        (filterv #(= (:user-id %) user-id))
                        (sort-by :read-at)
                        last
                        :read-at)}))

(schema/defn ^:always-validate counts :- [{:item-id lib-schema/UniqueID
                                           :count schema/Int
                                           :last-read-at (schema/maybe lib-schema/ISO8601)}]
  [db-opts item-ids :- [lib-schema/UniqueID] user-id :- lib-schema/UniqueID]
  (pmap (partial count-for db-opts user-id) item-ids))

(comment

  (require '[oc.lib.time :as oc-time])
  (require '[oc.lib.change.resources.read :as lib-read] :reload)

  (def db-opts {:table-prefix "local"})

  (far/list-tables db-opts)

  (far/delete-table db-opts (lib-read/table-name db-opts))
  (aprint
    (far/create-table db-opts
      (lib-read/table-name db-opts)
      [:item_id :s]
      {:range-keydef [:user_id :s]
       :billing-mode :pay-per-request
       :block? true}))

  (aprint
    (far/update-table db-opts
      (lib-read/table-name db-opts)
      {:gsindexes {:operation :create
                   :name (lib-read/user-id-gsi-name db-opts)
                   :billing-mode :pay-per-request
                   :hash-keydef [:user_id :s]
                   :range-keydef [:container_id :s]
                   :projection :all}}))

  ;; Add GSI for delete all via item-id
  (aprint
    @(far/update-table db-opts
      (lib-read/table-name db-opts)
      {:gsindexes {:operation :create
                   :name (lib-read/container-id-item-id-gsi-name db-opts)
                   :billing-mode :pay-per-request
                   :hash-keydef [:item_id :s]
                   :range-keydef [:container_id :s]
                   :projection :keys-only}}))

  ;; Add GSI for delete all via org-id
  (far/update-table db-opts read/table-name
   {:gsindexes {:operation :create
                :name read/org-id-user-id-gsi-name
                :billing-mode :pay-per-request
                :hash-keydef [:org_id :s]
                :range-keydef [:user_id :s]
                :projection :keys-only}})

  (doseq [item (far/query db-opts (lib-read/table-name db-opts) {:item_id [:eq "512b-4ad1-9924"]} {:index (lib-read/container-id-item-id-gsi-name db-opts)})]
    (aprint
      (far/delete-item db-opts (lib-read/table-name db-opts) {:item_id (:item_id item)
                                                              :user_id (:user_id item)})))

  ;; Add GSI for delete all via container-id
  (aprint @(far/update-table db-opts (lib-read/table-name db-opts)
            {:gsindexes
              {:operation :create
               :name (lib-read/container-id-gsi-name db-opts)
               :billing-mode :pay-per-request
               :hash-keydef [:container_id :s]
               :range-keydef [:user_id :s]
               :projection :keys-only}}))

  (doseq [item (far/query db-opts (lib-read/table-name db-opts) {:container_id [:eq "25a3-4692-bf02"]} {:index (lib-read/container-id-gsi-name db-opts)})]
    (aprint
      (far/delete-item db-opts (lib-read/table-name db-opts) {:item_id (:item_id item)
                                                              :user_id (:user_id item)})))

  (far/update-table db-opts (lib-read/table-name db-opts) {:gsindexes {:operation :delete :name (lib-read/container-id-gsi-name db-opts)}})

  (aprint (far/describe-table db-opts (lib-read/table-name db-opts)))

  ;; Store an item read
  (lib-read/store! db-opts "aaaa-aaaa-aaaa" "bbbb-bbbb-bbbb" "cccc-cccc-cccc"
                           "1111-1111-1111" "Albert Camus" "http//..." (oc-time/current-timestamp))

  (lib-read/retrieve-by-item db-opts iid)

  (lib-read/retrieve-by-item db-opts "cccc-cccc-cccc")
  (lib-read/retrieve-by-user db-opts "1111-1111-1111")
  (lib-read/retrieve-by-user-container db-opts "1111-1111-1111" "bbbb-bbbb-bbbb")
  (lib-read/retrieve-by-user-org db-opts "1111-1111-1111" "aaaa-aaaa-aaaa")

  (lib-read/store! db-opts "aaaa-aaaa-aaaa" "bbbb-bbbb-bbbb" "c1c1-c1c1-c1c1"
                           "2222-2222-2222" "Arthur Schopenhauer" "http//..." (oc-time/current-timestamp))

  (lib-read/retrieve-by-item db-opts "c1c1-c1c1-c1c1")
  (lib-read/retrieve-by-user db-opts "2222-2222-2222")
  (lib-read/retrieve-by-user-container db-opts "2222-2222-2222" "bbbb-bbbb-bbbb")
  (lib-read/retrieve-by-user-org db-opts "2222-2222-2222" "aaaa-aaaa-aaaa")

  (lib-read/store! db-opts "aaaa-aaaa-aaaa" "bbbb-bbbb-bbbb" "c1c1-c1c1-c1c1"
                           "1111-1111-1111" "Albert Camus" "http//..." (oc-time/current-timestamp))

  (lib-read/counts db-opts ["cccc-cccc-cccc" "c1c1-c1c1-c1c1"] "1111-1111-1111")

  (far/delete-table db-opts (lib-read/table-name db-opts))
)