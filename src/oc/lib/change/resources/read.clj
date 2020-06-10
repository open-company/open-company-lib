(ns oc.lib.change.resources.read
  "Retrieve tuples from read table of Change service"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [taoensso.timbre :as timbre]
            [oc.lib.schema :as lib-schema]))

;; Table and GSIndex names

(def table "_read")

(def part-table (str "_part" table))

(defn table-name [db-opts]
  (keyword (str (:table-prefix db-opts) table)))

(defn part-table-name [db-opts]
  (keyword (str (:table-prefix db-opts) part-table)))

(defn part-item-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) part-table "_gsi_item_id")))

(defn user-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) table "_gsi_user_id")))

(defn part-user-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) part-table "_gsi_user_id")))

(defn org-id-user-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) table "_gsi_org_id_user_id")))

(defn part-org-id-user-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) part-table "_gsi_org_id_user_id")))

(defn container-id-item-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) table "_gsi_container_id_item_id")))

(defn part-container-id-item-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) part-table "_gsi_container_id_item_id")))

(defn container-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) table "_gsi_container_id")))

(defn part-container-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) part-table "_gsi_container_id")))

(defn part-user-id-item-id-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) part-table "_gsi_user_id_item_id")))

;; Helpers

;; In theory, DynamoDB (and by extension, Faraday) support `{:return :count}` but it doesn't seem to be working
;; https://github.com/ptaoussanis/faraday/issues/91
(defn- count-for [db-opts user-id item-id]
  (let [results (far/query db-opts (table-name db-opts) {:item_id [:eq item-id]})]
    {:item-id item-id :count (count results) :last-read-at (:read_at (last (sort-by :read-at (filterv #(= (:user_id %) user-id) results))))}))

(defn- part-count-for [db-opts user-id part-id]
  (let [results (far/query db-opts (part-table-name db-opts) {:part_id [:eq part-id]})]
    {:part-id part-id :count (count results) :last-read-at (:read_at (last (sort-by :read-at (filterv #(= (:user_id %) user-id) results))))}))

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

(schema/defn ^:always-validate store-part!
  ;; Store a read record for the specified user and part
  ([db-opts
    org-id :-  lib-schema/UniqueID
    container-id :- lib-schema/UniqueID
    item-id :- lib-schema/UniqueID
    part-id :- lib-schema/UniqueID
    user-id :- lib-schema/UniqueID
    user-name :- schema/Str
    avatar-url :- (schema/maybe schema/Str)
    read-at :- lib-schema/ISO8601]
  (far/put-item db-opts (part-table-name db-opts) {
      :org_id org-id
      :container_id container-id
      :item_id item-id
      :part_id part-id
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

(schema/defn ^:always-validate retrieve-parts-by-item :- [{:user-id lib-schema/UniqueID
                                                           :part-id lib-schema/UniqueID}]
  [db-opts item-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (part-table-name db-opts) {:item_id [:eq item-id]} {:index (part-item-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:user_id :user-id :part_id :part-id}))
      (map #(select-keys % [:user-id :part-id]))))

(schema/defn ^:always-validate retrieve-by-part :- [{:user-id lib-schema/UniqueID
                                                     :name schema/Str
                                                     :avatar-url (schema/maybe schema/Str)
                                                     :read-at lib-schema/ISO8601}]
  [db-opts part-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (part-table-name db-opts) {:part_id [:eq part-id]})
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

(schema/defn ^:always-validate retrieve-parts-by-user :- [{(schema/optional-key :container-id) lib-schema/UniqueID
                                                           :item-id lib-schema/UniqueID
                                                           :part-id lib-schema/UniqueID
                                                           :read-at lib-schema/ISO8601}]
  [db-opts user-id :- lib-schema/UniqueID]
  (->>
      (far/query db-opts (part-table-name db-opts) {:user_id [:eq user-id]} {:index (part-user-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:container_id :container-id :item_id :item-id :part_id :part-id :read_at :read-at}))
      (map #(select-keys % [:container-id :item-id :part-id :read-at]))))

(schema/defn ^:always-validate retrieve-by-user-part :- {(schema/optional-key :org-id) lib-schema/UniqueID
                                                         (schema/optional-key :container-id) lib-schema/UniqueID
                                                         (schema/optional-key :item-id) lib-schema/UniqueID
                                                         (schema/optional-key :oart-id) lib-schema/UniqueID
                                                         (schema/optional-key :user-id) lib-schema/UniqueID
                                                         (schema/optional-key :name) schema/Str
                                                         (schema/optional-key :avatar-url) (schema/maybe schema/Str)
                                                         (schema/optional-key :read-at) lib-schema/ISO8601}
  [db-opts user-id :- lib-schema/UniqueID part-id :- lib-schema/UniqueID]
  (-> (far/get-item db-opts (part-table-name db-opts) {:part_id part-id
                                                       :user_id user-id})
      (clojure.set/rename-keys {:org_id :org-id :container_id :container-id :item_id :item-id :part_id :part-id :user_id :user-id :avatar_url :avatar-url :read_at :read-at})
      (select-keys [:org-id :container-id :item-id :part-id :user-id :name :avatar-url :read-at])))

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

(schema/defn ^:always-validate retrieve-parts-by-user-item :- [{(schema/optional-key :part-id) lib-schema/UniqueID
                                                                (schema/optional-key :user-id) lib-schema/UniqueID
                                                                (schema/optional-key :name) schema/Str
                                                                (schema/optional-key :avatar-url) (schema/maybe schema/Str)
                                                                (schema/optional-key :read-at) lib-schema/ISO8601}]
  [db-opts user-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (part-table-name db-opts) {:item_id [:eq item-id] :user_id [:eq user-id]} {:index (part-user-id-item-id-gsi-name db-opts)})
       (map #(clojure.set/rename-keys % {:part_id :part-id :user_id :user-id :avatar_url :avatar-url :read_at :read-at}))
       (map #(select-keys % [:part-id :user-id :name :avatar-url :read-at]))))

(schema/defn ^:always-validate retrieve-by-user-container :- [{:item-id lib-schema/UniqueID
                                                               :read-at lib-schema/ISO8601}]
  [db-opts user-id :- lib-schema/UniqueID container-id :- lib-schema/UniqueID]
  (->>
      (far/query db-opts (table-name db-opts) {:user_id [:eq user-id] :container_id [:eq container-id]}
                                              {:index (user-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:item_id :item-id :read_at :read-at}))
      (map #(select-keys % [:item-id :read-at]))))

(schema/defn ^:always-validate retrieve-parts-by-user-container :- [{:item-id lib-schema/UniqueID
                                                                     :part-id lib-schema/UniqueID
                                                                     :read-at lib-schema/ISO8601}]
  [db-opts user-id :- lib-schema/UniqueID container-id :- lib-schema/UniqueID]
  (->>
      (far/query db-opts (part-table-name db-opts) {:user_id [:eq user-id] :container_id [:eq container-id]} {:index (part-user-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:item_id :item-id :part_id :part-id :read_at :read-at}))
      (map #(select-keys % [:part-id :item-id :read-at]))))

(schema/defn ^:always-validate retrieve-by-user-org :- [{(schema/optional-key :item-id) lib-schema/UniqueID
                                                         (schema/optional-key :container-id) lib-schema/UniqueID
                                                         (schema/optional-key :read-at) lib-schema/ISO8601}]
  [db-opts user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (table-name db-opts) {:org_id [:eq org-id] :user_id [:eq user-id]} {:index (org-id-user-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:container_id :container-id :item_id :item-id :read_at :read-at}))
      (map #(select-keys % [:item-id :container-id :read-at]))))

(schema/defn ^:always-validate retrieve-parts-by-user-org :- [{(schema/optional-key :part-id) lib-schema/UniqueID
                                                               (schema/optional-key :item-id) lib-schema/UniqueID
                                                               (schema/optional-key :container-id) lib-schema/UniqueID
                                                               (schema/optional-key :read-at) lib-schema/ISO8601}]
  [db-opts user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (part-table-name db-opts) {:org_id [:eq org-id] :user_id [:eq user-id]} {:index (part-org-id-user-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:part_id :part-id :item_id :item-id :container_id :container-id :read_at :read-at}))
      (map #(select-keys % [:part-id :item-id :container-id :read-at]))))

(schema/defn ^:always-validate retrieve-by-container :- [{(schema/optional-key :user-id) lib-schema/UniqueID
                                                          (schema/optional-key :item-id) lib-schema/UniqueID}]
  [db-opts container-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (table-name db-opts) {:container_id [:eq container-id]} {:index (container-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:user_id :user-id :item_id :item-id :read_at :read-at}))
      (map #(select-keys % [:user-id :item-id :read-at]))))

(schema/defn ^:always-validate retrieve-parts-by-container :- [{(schema/optional-key :user-id) lib-schema/UniqueID
                                                                (schema/optional-key :part-id) lib-schema/UniqueID}]
  [db-opts container-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (part-table-name db-opts) {:container_id [:eq container-id]} {:index (part-container-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:user_id :user-id :part_id :part-id :item_id :item-id :read_at :read-at}))
      (map #(select-keys % [:user-id :part-id :item-id :read-at]))))

(schema/defn ^:always-validate retrieve-by-org :- [{(schema/optional-key :user-id) lib-schema/UniqueID
                                                    (schema/optional-key :item-id) lib-schema/UniqueID}]
  [db-opts org-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (table-name db-opts) {:org_id [:eq org-id]} {:index (org-id-user-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:user_id :user-id :item_id :item-id}))
      (map #(select-keys % [:user-id :item-id]))))

(schema/defn ^:always-validate retrieve-parts-by-org :- [{(schema/optional-key :user-id) lib-schema/UniqueID
                                                          (schema/optional-key :part-id) lib-schema/UniqueID}]
  [db-opts org-id :- lib-schema/UniqueID]
  (->> (far/query db-opts (part-table-name db-opts) {:org_id [:eq org-id]} {:index (part-org-id-user-id-gsi-name db-opts)})
      (map #(clojure.set/rename-keys % {:user_id :user-id :part_id :part-id}))
      (map #(select-keys % [:user-id :part-id]))))

;; Move

(schema/defn ^:always-validate move-item!
  [db-opts item-id :- lib-schema/UniqueID old-container-id :- lib-schema/UniqueID new-container-id :- lib-schema/UniqueID]
  (let [items-to-move (retrieve-by-item db-opts item-id)]
    (timbre/info "Read move-item! for" item-id "moving:" (count items-to-move) "items from container" old-container-id "to" new-container-id)
    (doseq [item items-to-move]
      (let [full-item (retrieve-by-user-item db-opts (:user-id item) (:item-id item))]
        (far/delete-item db-opts (table-name db-opts) {:item_id (:item-id full-item)
                                                       :user_id (:user-id full-item)})
        (far/put-item db-opts (table-name db-opts) {
          :org_id (:org-id full-item)
          :container_id new-container-id
          :item_id (:item-id full-item)
          :user_id (:user-id full-item)
          :name (:name full-item)
          :avatar_url (:avatar-url full-item)
          :read_at (:read-at full-item)}))))

  (let [parts-to-move (retrieve-by-user-part db-opts item-id)]
    (timbre/info "Read move-item! for" item-id "moving:" (count parts-to-move) "parts from container" old-container-id "to" new-container-id)
    (doseq [part parts-to-move]
      (let [full-part (retrieve-parts-by-user db-opts (:user_id part) (:part_id part))]
        (far/delete-item db-opts (part-table-name db-opts) {:part_id (:part-id full-part)
                                                            :user_id (:user-id full-part)})
        (far/put-item db-opts (part-table-name db-opts) {
          :org_id (:org-id full-part)
          :container_id new-container-id
          :item_id (:item-id full-part)
          :part_id (:part-id full-part)
          :user_id (:user-id full-part)
          :name (:name full-part)
          :avatar_url (:avatar-url full-part)
          :read_at (:read-at full-part)})))))

;; Delete

(schema/defn ^:always-validate delete-user-part!
  [db-opts user-id :- lib-schema/UniqueID part-id :- lib-schema/UniqueID]
  (far/delete-item db-opts (part-table-name db-opts) {:part_id part-id
                                                      :user_id user-id}))

(schema/defn ^:always-validate delete-by-part!

  ([db-opts container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID part-id :- lib-schema/UniqueID]
   (delete-by-part! db-opts part-id))

  ([db-opts item-id :- lib-schema/UniqueID part-id :- lib-schema/UniqueID]
   (delete-by-part! db-opts part-id))

  ([db-opts part-id :- lib-schema/UniqueID]
   (doseq [part (retrieve-by-part db-opts part-id)]
     (delete-user-part! db-opts (:user-id part) part-id))))

(schema/defn ^:always-validate delete-user-item!
  [db-opts user-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
  (doseq [part (retrieve-parts-by-user-item db-opts user-id item-id)]
    (delete-user-part! db-opts user-id (:part-id part)))
  (far/delete-item db-opts (table-name db-opts) {:item_id item-id
                                                 :user_id user-id}))

(def delete! delete-user-item!)

(schema/defn ^:always-validate delete-parts-by-item!

  ([db-opts container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
   (delete-parts-by-item! db-opts item-id))

  ([db-opts item-id :- lib-schema/UniqueID]
   ;; Remove all part reads
   (doseq [part (retrieve-parts-by-item db-opts item-id)]
     (delete-user-part! db-opts (:user-id part) (:part-id part)))))

(schema/defn ^:always-validate delete-by-item!

  ([db-opts container-id :- lib-schema/UniqueID item-id :- lib-schema/UniqueID]
   (delete-by-item! db-opts item-id))

  ([db-opts item-id :- lib-schema/UniqueID]
   ;; Remove all item reads
   (doseq [item (retrieve-by-item db-opts item-id)]
     (delete! db-opts (:user-id item) item-id))
   ;; Remove all part reads
   (delete-parts-by-item! db-opts item-id)))

(schema/defn ^:always-validate delete-by-container!
  [db-opts container-id :- lib-schema/UniqueID]
  ;; Remove all item reads
  (doseq [item (retrieve-by-container db-opts container-id)]
    (far/delete-item db-opts (table-name db-opts) {:item_id (:item-id item)
                                                   :user_id (:user-id item)}))
  ;; Remove all part reads
  (doseq [item (retrieve-parts-by-container db-opts container-id)]
    (far/delete-item db-opts (part-table-name db-opts) {:part_id (:part-id item)
                                                        :user_id (:user-id item)})))

(schema/defn ^:always-validate delete-by-org!
  [db-opts org-id :- lib-schema/UniqueID]
  ;; Remove all item reads
  (doseq [item (retrieve-by-org db-opts org-id)]
    (far/delete-item db-opts (table-name db-opts) {:item_id (:item-id item)
                                                   :user_id (:user-id item)}))
  ;; Remove all part reads
  (doseq [item (retrieve-parts-by-org db-opts org-id)]
    (far/delete-item db-opts (part-table-name db-opts) {:part_id (:part-id item)
                                                        :user_id (:user-id item)})))

;; Count

(schema/defn ^:always-validate counts :- [{:item-id lib-schema/UniqueID
                                           :count schema/Int
                                           :last-read-at (schema/maybe lib-schema/ISO8601)}]
  [db-opts item-ids :- [lib-schema/UniqueID] user-id :- lib-schema/UniqueID]
  (pmap (partial count-for db-opts user-id) item-ids))

(schema/defn ^:always-validate part-counts :- [{:part-id lib-schema/UniqueID
                                                :count schema/Int
                                                :last-read-at (schema/maybe lib-schema/ISO8601)}]
  [db-opts part-ids :- [lib-schema/UniqueID] user-id :- lib-schema/UniqueID]
  (pmap (partial part-count-for db-opts user-id) part-ids))

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

  (far/delete-table db-opts (lib-read/part-table-name db-opts))
  (aprint
    (far/create-table db-opts
      (lib-read/part-table-name db-opts)
      [:part_id :s]
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

  (aprint
    (far/update-table db-opts
      (lib-read/part-table-name db-opts)
      {:gsindexes {:operation :create
                   :name (lib-read/part-user-id-gsi-name db-opts)
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
  (aprint
    @(far/update-table db-opts
      (lib-read/part-table-name db-opts)
      {:gsindexes {:operation :create
                   :name (lib-read/part-container-id-item-id-gsi-name db-opts)
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

  (far/update-table db-opts read/part-table-name
   {:gsindexes {:operation :create
                :name read/part-org-id-user-id-gsi-name
                :billing-mode :pay-per-request
                :hash-keydef [:org_id :s]
                :range-keydef [:user_id :s]
                :projection :keys-only}})

  (doseq [item (far/query db-opts (lib-read/table-name db-opts) {:item_id [:eq "512b-4ad1-9924"]} {:index (lib-read/container-id-item-id-gsi-name db-opts)})]
    (aprint
      (far/delete-item db-opts (lib-read/table-name db-opts) {:item_id (:item_id item)
                                                              :user_id (:user_id item)})))

  (doseq [item (far/query db-opts (lib-read/part-table-name db-opts) {:item_id [:eq "512b-4ad1-9924"]} {:index (lib-read/part-container-id-item-id-gsi-name db-opts)})]
    (aprint
      (far/delete-item db-opts (lib-read/part-table-name db-opts) {:part_id (:part_id item)
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

  (aprint @(far/update-table db-opts (lib-read/part-table-name db-opts)
            {:gsindexes
              {:operation :create
               :name (lib-read/part-container-id-gsi-name db-opts)
               :billing-mode :pay-per-request
               :hash-keydef [:container_id :s]
               :range-keydef [:user_id :s]
               :projection :keys-only}}))

  (doseq [item (far/query db-opts (lib-read/table-name db-opts) {:container_id [:eq "25a3-4692-bf02"]} {:index (lib-read/container-id-gsi-name db-opts)})]
    (aprint
      (far/delete-item db-opts (lib-read/table-name db-opts) {:item_id (:item_id item)
                                                              :user_id (:user_id item)})))

  (doseq [item (far/query db-opts (lib-read/part-table-name db-opts) {:container_id [:eq "25a3-4692-bf02"]} {:index (lib-read/part-container-id-gsi-name db-opts)})]
    (aprint
      (far/delete-item db-opts (lib-read/part-table-name db-opts) {:part_id (:part_id item)
                                                                   :user_id (:user_id item)})))

  (far/update-table db-opts (lib-read/table-name db-opts) {:gsindexes {:operation :delete :name (lib-read/container-id-gsi-name db-opts)}})

  (far/update-table db-opts (lib-read/part-table-name db-opts) {:gsindexes {:operation :delete :name (lib-read/part-container-id-gsi-name db-opts)}})

  (aprint (far/describe-table db-opts (lib-read/table-name db-opts)))

  ;; Store an item read
  (lib-read/store! db-opts "aaaa-aaaa-aaaa" "bbbb-bbbb-bbbb" "cccc-cccc-cccc"
                           "1111-1111-1111" "Albert Camus" "http//..." (oc-time/current-timestamp))

  (lib-read/retrieve-by-item db-opts iid)
  (lib-read/retrieve-parts-by-item db-opts iid)

  ;; Store a part read
  (lib-read/store! db-opts "aaaa-aaaa-aaaa" "bbbb-bbbb-bbbb" "cccc-cccc-cccc" "dddd-dddd-dddd"
                           "1111-1111-1111" "Albert Camus" "http//..." (oc-time/current-timestamp))

  (lib-read/retrieve-by-item db-opts "cccc-cccc-cccc")
  (lib-read/retrieve-by-part db-opts "dddd-dddd-dddd")
  (lib-read/retrieve-by-user db-opts "1111-1111-1111")
  (lib-read/retrieve-by-user-container db-opts "1111-1111-1111" "bbbb-bbbb-bbbb")
  (lib-read/retrieve-by-user-org db-opts "1111-1111-1111" "aaaa-aaaa-aaaa")

  (lib-read/store! db-opts "aaaa-aaaa-aaaa" "bbbb-bbbb-bbbb" "c1c1-c1c1-c1c1"
                           "2222-2222-2222" "Arthur Schopenhauer" "http//..." (oc-time/current-timestamp))
  (lib-read/store! db-opts "aaaa-aaaa-aaaa" "bbbb-bbbb-bbbb" "c1c1-c1c1-c1c1" "d1d1-d1d1-d1d1"
                           "2222-2222-2222" "Arthur Schopenhauer" "http//..." (oc-time/current-timestamp))

  (lib-read/retrieve-by-item db-opts "c1c1-c1c1-c1c1")
  (lib-read/retrieve-by-user db-opts "2222-2222-2222")
  (lib-read/retrieve-by-user-container db-opts "2222-2222-2222" "bbbb-bbbb-bbbb")
  (lib-read/retrieve-by-user-org db-opts "2222-2222-2222" "aaaa-aaaa-aaaa")

  (lib-read/store! db-opts "aaaa-aaaa-aaaa" "bbbb-bbbb-bbbb" "c1c1-c1c1-c1c1"
                           "1111-1111-1111" "Albert Camus" "http//..." (oc-time/current-timestamp))

  (lib-read/counts db-opts ["cccc-cccc-cccc" "c1c1-c1c1-c1c1"] "1111-1111-1111")
  (lib-read/part-counts db-opts ["cccc-cccc-cccc" "c1c1-c1c1-c1c1"] "1111-1111-1111")

  (far/delete-table db-opts (lib-read/table-name db-opts))
  (far/delete-table db-opts (lib-read/part-table-name db-opts))
)