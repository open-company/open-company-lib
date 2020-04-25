(ns oc.lib.change.resources.follow
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [taoensso.timbre :as timbre]
            [oc.lib.schema :as lib-schema]
            [oc.lib.slugify :as slug]))

(defn table-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_follow")))

(defn org-slug-gsi-name [db-opts]
  (str (:table-prefix db-opts) "_follow_gsi_org_slug"))

(defn publisher-follower-table-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_publisher_follower")))

(defn org-slug-publisher-followers-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_publisher_follower_gsi_org_slug")))

(defn board-follower-table-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_board_follower")))

(defn org-slug-board-followers-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_board_follower_gsi_org_slug")))

(def Slug (schema/pred slug/valid-slug?))

(def ResourceType (schema/enum :user :board))

(schema/defn ^:always-validate retrieve
  :- {:user-id lib-schema/UniqueID :org-slug Slug :publisher-uuids (schema/maybe [lib-schema/UniqueID]) :board-uuids (schema/maybe [lib-schema/UniqueID])}
  [dynamodb-opts user-id :- lib-schema/UniqueID org-slug :- Slug]
  (if-let [user-item (far/get-item dynamodb-opts (table-name dynamodb-opts) {:user_id user-id
                                                                             :org_slug org-slug})]
    (clojure.set/rename-keys user-item {:user_id :user-id :org_slug :org-slug :publisher_uuids :publisher-uuids :board_uuids :board-uuids})
    {:user-id user-id :org-slug org-slug :publisher-uuids nil :board-uuids nil}))

(schema/defn ^:always-validate retrieve-all
  :- [{:user-id lib-schema/UniqueID :org-slug Slug :publisher-uuids (schema/maybe [lib-schema/UniqueID]) :board-uuids (schema/maybe [lib-schema/UniqueID])}]
  [dynamodb-opts org-slug :- Slug]
  (doseq [item (far/query dynamodb-opts (table-name dynamodb-opts) {:org_slug [:eq org-slug]} {:index (org-slug-gsi-name dynamodb-opts)})]
    (map #(clojure.set/rename-keys % {:user_id :user-id :org_slug :org-slug :publisher_uuids :publisher-uuids :board_uuids :board-uuids}))))

(schema/defn ^:always-validate retrieve-publisher-followers
  :- {:publisher-uuid lib-schema/UniqueID :org-slug Slug :follower-uuids (schema/maybe [lib-schema/UniqueID])}
  [dynamodb-opts org-slug :- Slug publisher-uuid :- lib-schema/UniqueID]
  (if-let [item (far/get-item dynamodb-opts (publisher-follower-table-name dynamodb-opts) {:publisher_uuid publisher-uuid
                                                                                           :org_slug org-slug})]
    (clojure.set/rename-keys item {:publisher_uuid :publisher-uuid :org_slug :org-slug :follower_uuids :follower-uuids})
    {:publisher-uuid publisher-uuid :org-slug org-slug :follower-uuids nil}))

(schema/defn ^:always-validate retrieve-board-followers
  :- {:board-uuid lib-schema/UniqueID :org-slug Slug :follower-uuids (schema/maybe [lib-schema/UniqueID])}
  [dynamodb-opts org-slug :- Slug board-uuid :- lib-schema/UniqueID]
  (if-let [item (far/get-item dynamodb-opts (board-follower-table-name dynamodb-opts) {:board_uuid board-uuid
                                                                                       :org_slug org-slug})]
    (clojure.set/rename-keys item {:board_uuid :board-uuid :org_slug :org-slug :follower_uuids :follower-uuids})
    {:board-uuid board-uuid :org-slug org-slug :follower-uuids nil}))

(schema/defn ^:always-validate retrieve-all-publisher-followers
  :- [{:org-slug Slug :follower-uuids [lib-schema/UniqueID] :publisher-uuid lib-schema/UniqueID :resource-type ResourceType}]
  [dynamodb-opts org-slug :- Slug]
  (let [followers (far/query dynamodb-opts (publisher-follower-table-name dynamodb-opts) {:org_slug [:eq org-slug]} {:index (org-slug-publisher-followers-gsi-name dynamodb-opts)})]
    (mapv #(-> %
            (clojure.set/rename-keys {:org_slug :org-slug
                                      :publisher_uuid :publisher-uuid
                                      :follower_uuids :follower-uuids})
            (assoc :resource-type :user))
     followers)))

(schema/defn ^:always-validate retrieve-all-board-followers
  :- [{:org-slug Slug :follower-uuids [lib-schema/UniqueID] :board-uuid lib-schema/UniqueID :resource-type ResourceType}]
  [dynamodb-opts org-slug :- Slug]
  (let [followers (far/query dynamodb-opts (board-follower-table-name dynamodb-opts)
                   {:org_slug [:eq org-slug]}
                   {:index (org-slug-board-followers-gsi-name dynamodb-opts)})]
    (mapv #(-> %
            (clojure.set/rename-keys {:org_slug :org-slug
                                      :board_uuid :board-uuid
                                      :follower_uuids :follower-uuids})
            (assoc :resource-type :board))
     followers)))

(schema/defn ^:always-validate delete!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-slug :- Slug]
  (far/delete-item dynamodb-opts (table-name dynamodb-opts) {:user_id user-id
                                                             :org_slug org-slug})
  (let [publisher-followers (->> (far/query dynamodb-opts (publisher-follower-table-name dynamodb-opts)
                                  {:org_slug [:eq org-slug]}
                                  {:index (org-slug-publisher-followers-gsi-name dynamodb-opts)
                                   :filter-expr "contains(#k, :v)"
                                   :expr-attr-names {"#k" "follower_uuids"}
                                   :expr-attr-vals {":v" user-id}})
                              (map #(clojure.set/rename-keys % {:publisher_uuid :publisher-uuid :org_slug :org-slug :follower_uuids :follower-uuids})))]
    (doseq [follower publisher-followers
            :let [next-followers (vec (disj (set (:follower-uuids follower)) user-id))]]
      (far/put-item dynamodb-opts (publisher-follower-table-name dynamodb-opts)
       {:publisher_uuid (:publisher-uuid follower)
        :org_slug (:org-slug follower)
        :follower_uuids next-followers})))

  (let [board-followers (->> (far/query dynamodb-opts (board-follower-table-name dynamodb-opts)
                              {:org_slug [:eq org-slug]}
                              {:index (org-slug-board-followers-gsi-name dynamodb-opts)
                               :filter-expr "contains(#k, :v)"
                               :expr-attr-names {"#k" "follower_uuids"}
                               :expr-attr-vals {":v" user-id}})
                          (map #(clojure.set/rename-keys % {:board_uuid :board-uuid :org_slug :org-slug :follower_uuids :follower-uuids})))]
    (doseq [follower board-followers
            :let [next-followers (vec (disj (set (:follower-uuids follower)) user-id))]]
      (far/put-item dynamodb-opts (board-follower-table-name dynamodb-opts)
       {:board_uuid (:board-uuid follower)
        :org_slug (:org-slug follower)
        :follower_uuids next-followers})))
  true)

(schema/defn ^:always-validate delete-by-org!
  [dynamodb-opts org-slug :- Slug]
  (doseq [item (far/query dynamodb-opts (table-name dynamodb-opts)
                {:org_slug [:eq org-slug]} {:index (org-slug-gsi-name dynamodb-opts)})]
    (delete! dynamodb-opts (:user_id item) org-slug))
  (doseq [item (far/query dynamodb-opts (publisher-follower-table-name dynamodb-opts)
                {:org_slug [:eq org-slug]} {:index (org-slug-publisher-followers-gsi-name dynamodb-opts)})]
    (delete! dynamodb-opts (:user_id item) org-slug))
  (doseq [item (far/query dynamodb-opts (publisher-follower-table-name dynamodb-opts)
                {:org_slug [:eq org-slug]} {:index (org-slug-publisher-followers-gsi-name dynamodb-opts)})]
    (delete! dynamodb-opts (:user_id item) org-slug))

  (far/delete-item dynamodb-opts (table-name dynamodb-opts) {:org_slug [:eq org-slug]})
  true)

(schema/defn ^:always-validate store!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-slug :- Slug publisher-uuids :- [lib-schema/UniqueID] board-uuids :- [lib-schema/UniqueID]]
  (let [prev-item (retrieve dynamodb-opts user-id org-slug)
        losing-publisher-uuids (clojure.set/difference (set (:publisher-uuids prev-item)) (set publisher-uuids))
        losing-board-uuids (clojure.set/difference (set (:board-uuids prev-item)) (set board-uuids))]
    (far/put-item dynamodb-opts (table-name dynamodb-opts) {
        :user_id user-id
        :org_slug org-slug
        :publisher_uuids (or publisher-uuids [])
        :board_uuids (or board-uuids [])})
    (doseq [u publisher-uuids]
      (let [follower-item (retrieve-publisher-followers dynamodb-opts org-slug u)
            next-followers (vec (conj (set (:follower-uuids follower-item)) user-id))]
        (far/put-item dynamodb-opts (publisher-follower-table-name dynamodb-opts) {:publisher_uuid u
                                                                                   :org_slug org-slug
                                                                                   :follower_uuids next-followers})))
    (doseq [u losing-publisher-uuids]
      (let [follower-item (retrieve-publisher-followers dynamodb-opts org-slug u)
            next-followers (vec (disj (set (:follower-uuids follower-item)) user-id))]
        (far/put-item dynamodb-opts (publisher-follower-table-name dynamodb-opts) {:publisher_uuid u
                                                                                   :org_slug org-slug
                                                                                   :follower_uuids next-followers})))
    (doseq [b board-uuids]
      (let [follower-item (retrieve-board-followers dynamodb-opts org-slug b)
            next-followers (vec (conj (set (:follower-uuids follower-item)) user-id))]
        (far/put-item dynamodb-opts (board-follower-table-name dynamodb-opts) {:board_uuid b
                                                                               :org_slug org-slug
                                                                               :follower_uuids next-followers})))
    (doseq [b losing-board-uuids]
      (let [follower-item (retrieve-board-followers dynamodb-opts org-slug b)
            next-followers (vec (disj (set (:follower-uuids follower-item)) user-id))]
        (far/put-item dynamodb-opts (board-follower-table-name dynamodb-opts) {:board_uuid b
                                                                               :org_slug org-slug
                                                                               :follower_uuids next-followers})))
    true))

(schema/defn ^:always-validate store-publishers!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-slug :- Slug publisher-uuids :- [lib-schema/UniqueID]]
  (let [item (retrieve dynamodb-opts user-id org-slug)]
    (store! dynamodb-opts user-id org-slug (vec (set publisher-uuids)) (or (:board-uuids item) [])))
  true)

(schema/defn ^:always-validate store-boards!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-slug :- Slug board-uuids :- [lib-schema/UniqueID]]
  (let [item (retrieve dynamodb-opts user-id org-slug)]
    (store! dynamodb-opts user-id org-slug (or (:publisher-uuids item) []) (vec (set board-uuids))))
  true)

(schema/defn ^:always-validate follow-publisher!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-slug :- Slug publisher-uuid :- lib-schema/UniqueID]
  (let [item (retrieve dynamodb-opts user-id org-slug)
        next-publisher-uuids (if (seq (:publisher-uuids item))
                               (vec (clojure.set/union (set (:publisher-uuids item)) #{publisher-uuid}))
                               [publisher-uuid])]
    (store! dynamodb-opts user-id org-slug next-publisher-uuids (or (:board-uuids item) [])))
  true)

(schema/defn ^:always-validate follow-board!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-slug :- Slug board-uuid :- lib-schema/UniqueID]
  (let [item (retrieve dynamodb-opts user-id org-slug)
        next-board-uuids (if (seq (:board-uuids item))
                           (vec (clojure.set/union (set (:board-uuids item)) #{board-uuid}))
                           [board-uuid])]
    (store! dynamodb-opts user-id org-slug (or (:publisher-uuids item) []) next-board-uuids))
  true)

(schema/defn ^:always-validate unfollow-publisher!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-slug :- Slug publisher-uuid :- lib-schema/UniqueID]
  (let [item (retrieve dynamodb-opts user-id org-slug)
        next-publisher-uuids (if (seq (:publisher-uuids item))
                               (vec (clojure.set/difference (set (:publisher-uuids item)) #{publisher-uuid}))
                               [])]
    (store! dynamodb-opts user-id org-slug next-publisher-uuids (or (:board-uuids item) [])))
  true)

(schema/defn ^:always-validate unfollow-board!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-slug :- Slug board-uuid :- lib-schema/UniqueID]
  (let [item (retrieve dynamodb-opts user-id org-slug)
        next-board-uuids (if (seq (:board-uuids item))
                           (vec (clojure.set/difference (set (:board-uuids item)) #{board-uuid}))
                           [])]
    (store! dynamodb-opts user-id org-slug (or (:publisher-uuids item) []) next-board-uuids))
  true)

(comment

  "Example data:

   Following:
    [{:user_id 1111-1111-1111
      :org_slug carrot
      :publisher_uuids [2222-2222-2222 3333-3333-3333]
      :board_uuids [aaaa-aaaa-aaaa bbbb-bbbb-bbbb]}

     {:user_id 2222-2222-2222
      :org_slug carrot
      :publisher_uuids [3333-3333-3333 4444-4444-4444]
      :board_uuids [bbbb-bbbb-bbbb cccc-cccc-cccc]}]

   Publisher followers
   [{:publisher_uuid 3333-3333-3333
     :org_slug carrot
     :follower_uuids [1111-1111-1111 2222-2222-2222]}]

   Board followers
   [{:board_uuid bbbb-bbbb-bbbb
     :org_slug carrot
     :follower_uuids [1111-1111-1111 2222-2222-2222]}]
  "

  (require '[oc.lib.change.resources.follow :as follow] :reload)

  (far/list-tables dynamodb-opts)

  (far/delete-table dynamodb-opts (follow/table-name dynamodb-opts))
  (aprint
   (far/create-table dynamodb-opts
     (follow/table-name dynamodb-opts)
     [:user_id :s]
     {:range-keydef [:org_slug :s]
      :billing-mode :pay-per-request
      :block? true}))

  (aprint
   @(far/update-table dynamodb-opts
     (follow/table-name dynamodb-opts)
     {:gsindexes {:operation :create
                  :name (follow/org-slug-gsi-name dynamodb-opts)
                  :billing-mode :pay-per-request
                  :hash-keydef [:org_slug :s]
                  :range-keydef [:user_id :s]
                  :projection :keys-only}}))

  ;; Publisher followers

  (far/delete-table dynamodb-opts (follow/publisher-follower-table-name dynamodb-opts))
  (aprint
   (far/create-table dynamodb-opts
     (follow/publisher-follower-table-name dynamodb-opts)
     [:publisher_uuid :s]
     {:range-keydef [:org_slug :s]
      :billing-mode :pay-per-request
      :block? true}))

  ;; Board followers

  (far/delete-table dynamodb-opts (follow/board-follower-table-name dynamodb-opts))
  (aprint
   (far/create-table dynamodb-opts
     (follow/board-follower-table-name dynamodb-opts)
     [:board_uuid :s]
     {:range-keydef [:org_slug :s]
      :billing-mode :pay-per-request
      :block? true}))

  (aprint (far/describe-table dynamodb-opts (follow/table-name dynamodb-opts)))

  (follow/store! dynamodb-opts "1111-1111-1111" "aaaa-aaaa-aaaa" ["2222-2222-2222" "3333-3333-3333"])

  (follow/follow! dynamodb-opts "1111-1111-1111" "aaaa-aaaa-aaaa" "4444-4444-4444")

  (follow/unfollow! dynamodb-opts "1111-1111-1111" "aaaa-aaaa-aaaa" "2222-2222-2222")

  (follow/retrieve dynamodb-opts "1111-1111-1111" "aaaa-aaaa-aaaa")
  (follow/retrieve-all dynamodb-opts "aaaa-aaaa-aaaa")

  (follow/delete! dynamodb-opts "1111-1111-1111" "aaaa-aaaa-aaaa")

  (follow/delete-by-org! dynamodb-opts "aaaa-aaaa-aaaa"))