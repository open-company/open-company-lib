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

(defn follower-table-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_follower")))

(def Slug (schema/pred slug/valid-slug?))

(schema/defn ^:always-validate retrieve
  :- {:user-id lib-schema/UniqueID :publisher-uuids [lib-schema/UniqueID] :org-slug Slug}
  [dynamodb-opts user-id :- lib-schema/UniqueID org-slug :- Slug]
  (if-let [user-item (far/get-item dynamodb-opts (table-name dynamodb-opts) {:user_id user-id
                                                                             :org_slug org-slug})]
    (clojure.set/rename-keys user-item {:user_id :user-id :publisher_uuids :publisher-uuids :org_slug :org-slug})
    {:user-id user-id :org-slug org-slug :publisher-uuids []}))

(schema/defn ^:always-validate retrieve-all
  :- [{:user-id lib-schema/UniqueID :org_slug Slug :publisher-uuids [lib-schema/UniqueID]}]
  [dynamodb-opts org-slug :- Slug]
  (doseq [item (far/query dynamodb-opts (table-name dynamodb-opts) {:org_slug org-slug} {:index org-slug-gsi-name})]
    (map #(clojure.set/rename-keys % {:user_id :user-id :org_slug :org-slug :publisher_uuids :publisher-uuids}))))

(schema/defn ^:always-validate retrieve-followers
  :- {:publisher-uuid lib-schema/UniqueID :org-slug Slug :followers [lib-schema/UniqueID]}
  [dynamodb-opts publisher-uuid :- lib-schema/UniqueID org-slug :- Slug]
  (if-let [item (far/get-item dynamodb-opts (follower-table-name dynamodb-opts) {:publisher_uuid publisher-uuid
                                                                                 :org_slug org-slug})]
    (clojure.set/rename-keys item {:publisher_uuid :publisher-uuid :org_slug :org-slug})
    {:publisher-uuid publisher-uuid :org-slug org-slug :followers []}))

(schema/defn ^:always-validate delete!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-slug :- Slug]
  (far/delete-item dynamodb-opts (table-name dynamodb-opts) {:user_id user-id
                                                             :org_slug org-slug})
  true)

(schema/defn ^:always-validate delete-by-org!
  [dynamodb-opts org-slug :- Slug]
  (doseq [item (far/query dynamodb-opts (table-name dynamodb-opts)
                {:org_slug [:eq org-slug]} {:index org-slug-gsi-name})]
    (delete! (:user_id item) org-slug))
  true)

(schema/defn ^:always-validate store!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-slug :- Slug publisher-uuids :- [lib-schema/UniqueID]]
  (far/put-item dynamodb-opts (table-name dynamodb-opts) {
      :user_id user-id
      :org_slug org-slug
      :publisher_uuids publisher-uuids})
  (doseq [u publisher-uuids]
    (let [follower-item (far/get-item dynamodb-opts (follower-table-name dynamodb-opts) {:publisher_uuid u
                                                                                         :org_slug org-slug})
          next-followers (vec (conj (set (:followers follower-item)) user-id))]
      (far/put-item dynamodb-opts (follower-table-name dynamodb-opts) {:publisher_uuid u
                                                                       :org_slug org-slug
                                                                       :followers next-followers})))
  true)

(schema/defn ^:always-validate follow!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-slug :- Slug publisher-uuid :- lib-schema/UniqueID]
  (let [item (retrieve user-id org-slug)
        new-publisher-uuids (if (seq (:publisher_uuids item))
                              (vec (clojure.set/union (set (:publisher_uuids item)) #{publisher-uuid}))
                              [publisher-uuid])]
    (store! dynamodb-opts user-id org-slug new-publisher-uuids))
  true)

(schema/defn ^:always-validate unfollow!
  [dynamodb-opts user-id :- lib-schema/UniqueID org-slug :- Slug publisher-uuid :- lib-schema/UniqueID]
  (let [item (retrieve user-id org-slug)
        new-publisher-uuids (if (seq (:publisher_uuids item))
                              (vec (clojure.set/difference (set (:publisher_uuids item)) #{publisher-uuid}))
                              [])]
    (if (seq new-publisher-uuids)
      (store! dynamodb-opts user-id org-slug new-publisher-uuids)
      (delete! dynamodb-opts user-id org-slug)))
  true)

(comment

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
                  :name follow/org-slug-gsi-name
                  :billing-mode :pay-per-request
                  :hash-keydef [:org_slug :s]
                  :range-keydef [:user_id :s]
                  :projection :keys-only}}))

  ;; Followers

  (far/delete-table dynamodb-opts (follow/follower-table-name dynamodb-opts))
  (aprint
   (far/create-table dynamodb-opts
     (follow/follower-table-name dynamodb-opts)
     [:publisher_uuid :s]
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