(ns oc.lib.change.resources.follow
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]
            [clojure.set :as clj-set]))

(defn table-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_follow")))

(defn org-id-gsi-name [db-opts]
  (str (:table-prefix db-opts) "_follow_gsi_org_id"))

(defn board-unfollower-table-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_board_unfollower")))

(defn org-id-board-unfollowers-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_board_unfollower_gsi_org_id")))

(def ResourceType (schema/enum :user :board))

(schema/defn ^:always-validate retrieve
  :- {:user-id lib-schema/UniqueID :org-id lib-schema/UniqueID :unfollow-board-uuids (schema/maybe [lib-schema/UniqueID])}
  [dynamodb-opts user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID]
  (if-let [user-item (far/get-item dynamodb-opts (table-name dynamodb-opts) {:user_id user-id
                                                                             :org_id org-id})]
    (clj-set/rename-keys user-item {:user_id :user-id
                                    :org_id :org-id
                                    :unfollow_board_uuids :unfollow-board-uuids})
    {:user-id user-id :org-id org-id :unfollow-board-uuids nil}))

(schema/defn ^:always-validate retrieve-all
  :- [{:user-id lib-schema/UniqueID :org-id lib-schema/UniqueID :unfollow-board-uuids (schema/maybe [lib-schema/UniqueID])}]
  [dynamodb-opts org-id :- lib-schema/UniqueID]
  (map #(clj-set/rename-keys % {:user_id :user-id
                                :org_id :org-id
                                :unfollow_board_uuids :unfollow-board-uuids})
       (far/query dynamodb-opts (table-name dynamodb-opts) {:org_id [:eq org-id]} {:index (org-id-gsi-name dynamodb-opts)})))

(schema/defn ^:always-validate retrieve-board-unfollowers
  :- {:board-uuid lib-schema/UniqueID :org-id lib-schema/UniqueID :unfollower-uuids (schema/maybe [lib-schema/UniqueID])}
  [dynamodb-opts org-id :- lib-schema/UniqueID board-uuid :- lib-schema/UniqueID]
  (if-let [item (far/get-item dynamodb-opts (board-unfollower-table-name dynamodb-opts) {:board_uuid board-uuid
                                                                                         :org_id org-id})]
    (clj-set/rename-keys item {:board_uuid :board-uuid :org_id :org-id :unfollower_uuids :unfollower-uuids})
    {:board-uuid board-uuid :org-id org-id :unfollower-uuids nil}))

(schema/defn ^:always-validate retrieve-all-board-unfollowers
  :- [{:org-id lib-schema/UniqueID :unfollower-uuids [lib-schema/UniqueID] :board-uuid lib-schema/UniqueID :resource-type ResourceType}]
  [dynamodb-opts org-id :- lib-schema/UniqueID]
  (let [followers (far/query dynamodb-opts (board-unfollower-table-name dynamodb-opts)
                   {:org_id [:eq org-id]}
                   {:index (org-id-board-unfollowers-gsi-name dynamodb-opts)})]
    (mapv #(-> %
            (clj-set/rename-keys {:org_id :org-id
                                      :board_uuid :board-uuid
                                      :unfollower_uuids :unfollower-uuids})
            (assoc :resource-type :board))
     followers)))

(schema/defn ^:always-validate delete!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID]
  (far/delete-item dynamodb-opts (table-name dynamodb-opts) {:user_id user-id
                                                             :org_id org-id})
  (let [board-unfollowers (->> (far/query dynamodb-opts (board-unfollower-table-name dynamodb-opts)
                              {:org_id [:eq org-id]}
                              {:index (org-id-board-unfollowers-gsi-name dynamodb-opts)
                               :filter-expr "contains(#k, :v)"
                               :expr-attr-names {"#k" "unfollower_uuids"}
                               :expr-attr-vals {":v" user-id}})
                          (map #(clj-set/rename-keys % {:board_uuid :board-uuid :org_id :org-id :unfollower_uuids :unfollower-uuids})))]
    (doseq [unfollower board-unfollowers
            :let [next-unfollowers (vec (disj (set (:unfollower-uuids unfollower)) user-id))]]
      (far/put-item dynamodb-opts (board-unfollower-table-name dynamodb-opts)
       {:board_uuid (:board-uuid unfollower)
        :org_id (:org-id unfollower)
        :unfollower_uuids next-unfollowers})))
  true)

(schema/defn ^:always-validate delete-by-org!
  [dynamodb-opts org-id :- lib-schema/UniqueID]

  (doseq [item (far/query dynamodb-opts (table-name dynamodb-opts)
                {:org_id [:eq org-id]} {:index (org-id-gsi-name dynamodb-opts)})]
    (delete! dynamodb-opts (:user_id item) org-id))

  (doseq [item (far/query dynamodb-opts (board-unfollower-table-name dynamodb-opts)
                {:org_id [:eq org-id]} {:index (org-id-board-unfollowers-gsi-name dynamodb-opts)})]
    (delete! dynamodb-opts (:board_uuid item) org-id))

  (far/delete-item dynamodb-opts (table-name dynamodb-opts) {:org_id [:eq org-id]})
  true)

(schema/defn ^:always-validate store!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID unfollow-board-uuids :- [lib-schema/UniqueID]]
  (let [prev-item (retrieve dynamodb-opts user-id org-id)
        losing-unfollow-board-uuids (clj-set/difference (set (:unfollow-board-uuids prev-item)) (set unfollow-board-uuids))]
    (far/put-item dynamodb-opts (table-name dynamodb-opts) {
        :user_id user-id
        :org_id org-id
        :unfollow_board_uuids (or unfollow-board-uuids [])})
    (doseq [b unfollow-board-uuids]
      (let [follower-item (retrieve-board-unfollowers dynamodb-opts org-id b)
            next-followers (vec (conj (set (:unfollower-uuids follower-item)) user-id))]
        (far/put-item dynamodb-opts (board-unfollower-table-name dynamodb-opts) {:board_uuid b
                                                                                 :org_id org-id
                                                                                 :unfollower_uuids next-followers})))
    (doseq [b losing-unfollow-board-uuids]
      (let [follower-item (retrieve-board-unfollowers dynamodb-opts org-id b)
            next-followers (vec (disj (set (:unfollower-uuids follower-item)) user-id))]
        (far/put-item dynamodb-opts (board-unfollower-table-name dynamodb-opts) {:board_uuid b
                                                                                 :org_id org-id
                                                                                 :unfollower_uuids next-followers})))
    true))

(schema/defn ^:always-validate store-boards!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID unfollow-board-uuids :- [lib-schema/UniqueID]]
  (let [item (retrieve dynamodb-opts user-id org-id)]
    (store! dynamodb-opts user-id org-id (vec (set unfollow-board-uuids))))
  true)

(schema/defn ^:always-validate follow-board!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID board-uuid :- lib-schema/UniqueID]
  (let [item (retrieve dynamodb-opts user-id org-id)
        next-unfollow-board-uuids (if (seq (:unfollow-board-uuids item))
                                    (vec (clj-set/difference (set (:unfollow-board-uuids item)) #{board-uuid}))
                                    [])]
    (store! dynamodb-opts user-id org-id next-unfollow-board-uuids))
  true)

(schema/defn ^:always-validate unfollow-board!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-id :- lib-schema/UniqueID board-uuid :- lib-schema/UniqueID]
  (let [item (retrieve dynamodb-opts user-id org-id)
        next-unfollow-board-uuids (if (seq (:unfollow-board-uuids item))
                                    (vec (clj-set/union (set (:unfollow-board-uuids item)) #{board-uuid}))
                                    [board-uuid])]
    (store! dynamodb-opts user-id org-id next-unfollow-board-uuids))
  true)

(comment

  "Example data:

   Following:
    [{:user_id 1111-1111-1111
      :org_id carrot
      :unfollow_board_uuids [aaaa-aaaa-aaaa bbbb-bbbb-bbbb]}

     {:user_id 2222-2222-2222
      :org_id carrot
      :unfollow_board_uuids [bbbb-bbbb-bbbb cccc-cccc-cccc]}]

   Board unfollowers
   [{:unfollow_board_uuid bbbb-bbbb-bbbb
     :org_id carrot
     :follower_uuids [1111-1111-1111 2222-2222-2222]}]
  "

  (require '[oc.lib.change.resources.follow :as follow] :reload)

  (far/list-tables dynamodb-opts)

  (far/delete-table dynamodb-opts (follow/table-name dynamodb-opts))
  (aprint
   (far/create-table dynamodb-opts
     (follow/table-name dynamodb-opts)
     [:user_id :s]
     {:range-keydef [:org_id :s]
      :billing-mode :pay-per-request
      :block? true}))

  (aprint
   @(far/update-table dynamodb-opts
     (follow/table-name dynamodb-opts)
     {:gsindexes {:operation :create
                  :name (follow/org-id-gsi-name dynamodb-opts)
                  :billing-mode :pay-per-request
                  :hash-keydef [:org_id :s]
                  :range-keydef [:user_id :s]
                  :projection :keys-only}}))

  ;; Board followers

  (far/delete-table dynamodb-opts (follow/board-unfollower-table-name dynamodb-opts))
  (aprint
   (far/create-table dynamodb-opts
     (follow/board-unfollower-table-name dynamodb-opts)
     [:unfollow_board_uuid :s]
     {:range-keydef [:org_id :s]
      :billing-mode :pay-per-request
      :block? true}))

  (aprint (far/describe-table dynamodb-opts (follow/table-name dynamodb-opts)))

  (follow/store! dynamodb-opts "1111-1111-1111" "aaaa-aaaa-aaaa" ["3333-3333-3333"] ["2222-2222-2222"])

  (follow/follow! dynamodb-opts "1111-1111-1111" "aaaa-aaaa-aaaa" "4444-4444-4444")

  (follow/unfollow! dynamodb-opts "1111-1111-1111" "aaaa-aaaa-aaaa" "2222-2222-2222")

  (follow/retrieve dynamodb-opts "1111-1111-1111" "aaaa-aaaa-aaaa")
  (follow/retrieve-all dynamodb-opts "aaaa-aaaa-aaaa")

  (follow/delete! dynamodb-opts "1111-1111-1111" "aaaa-aaaa-aaaa")

  (follow/delete-by-org! dynamodb-opts "aaaa-aaaa-aaaa"))