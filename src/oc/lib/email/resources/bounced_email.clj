(ns oc.lib.email.resources.bounced-email
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [taoensso.timbre :as timbre]
            [oc.lib.time :as oc-time]
            [oc.lib.schema :as lib-schema]
            [clojure.set :as clj-set]))

(defn table-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_bounced_email")))

(defn resource-type-gsi-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_resource_type")))

(def HardBounce :hard-bounce)

(def ResourceType (schema/enum HardBounce))

(def BouncedEmail {
  :email lib-schema/NonBlankStr
  :resource-type ResourceType
  :created-at lib-schema/ISO8601
  :updated-at lib-schema/ISO8601
  :bounce-count schema/Int
})

(def BouncedEmail->bounced-email
  {:resource_type :resource-type
   :bounce_count :bounce-count
   :created_at :created-at
   :updated_at :updated-at})

(def bounced-email->BouncedEmail
  {:resource-type :resource_type
   :bounce-count :bounce_count
   :created-at :created_at
   :updated-at :updated_at})

(defn- ->dyn [clj-map]
  (clj-set/rename-keys clj-map bounced-email->BouncedEmail))

(defn- ->clj [dyn-map]
  (-> dyn-map
      (clj-set/rename-keys BouncedEmail->bounced-email)
      (update :resource-type keyword)))

(schema/defn ^:always-validate retrieve-email
  :- (schema/maybe BouncedEmail)
  [db-opts email :- lib-schema/NonBlankStr]
  (let [key-idnx {:email email :resource-type HardBounce}
        dyn-key-idnx (->dyn key-idnx)]
    (timbre/tracef "Retrieve bounced-email %s with key %s" email dyn-key-idnx)
    (when-let [email-record (far/get-item db-opts (table-name db-opts) dyn-key-idnx)]
      (->clj email-record))))

(schema/defn ^:always-validate retrieve-hard-bounce
  :- [BouncedEmail]
  [db-opts]
  (timbre/trace "Retrieve all bounced-email")
  (map ->clj
       (far/query db-opts (table-name db-opts) {:resource_type [:eq HardBounce]} {:index (resource-type-gsi-name db-opts)})))

(schema/defn ^:always-validate delete-hard-bounce!
  [db-opts email :- lib-schema/NonBlankStr]
  (let [key-idnx {:email email :resource-type HardBounce}
        dyn-key-idnx (->dyn key-idnx)
        existing-item (retrieve-email db-opts email)]
    (timbre/tracef "Delete hard bounce for email %s with key %s" email dyn-key-idnx)
    (timbre/tracef "Retrieved item: %s" existing-item)
    (when existing-item
      (far/delete-item db-opts (table-name db-opts) dyn-key-idnx))))

(schema/defn ^:always-validate store-hard-bounce!
  [db-opts email :- lib-schema/NonBlankStr]
  (timbre/tracef "Store hard bounce for email %s" email)
  (if-let [prev-item (retrieve-email db-opts email)]
    (let [updt-formulae "SET #ka = :va, #kb = :vb"
          updt-attr-names {"#ka" (name :bounce_count)
                           "#kb" (name :updated_at)}
          updt-attr-vals {":va" (-> prev-item :bounce-count inc)
                          ":vb" (oc-time/current-timestamp)}
          key-indx {:email email
                    :resource-type HardBounce}
          dyn-key-indx (->dyn key-indx)]
      (timbre/tracef "Email %s already present in db: %s" email prev-item)
      (timbre/tracef "Updating record with index: %s, new bounce count: %d" dyn-key-indx (-> prev-item :bounce-count inc))
      (far/update-item db-opts (table-name db-opts) dyn-key-indx
                     {:update-expr updt-formulae
                      :expr-attr-names updt-attr-names
                      :expr-attr-vals updt-attr-vals
                      :return :all-new}))
    (let [ts (oc-time/current-timestamp)
          new-item {:email email :resource-type HardBounce :bounce-count 1 :created-at ts :updated-at ts}
          dyn-new-item (->dyn new-item)]
      (timbre/tracef "Email %s not found in DB, creating a new record with 1 bounce" email)
      (timbre/tracef "Will insert record: %s" dyn-new-item)
      (far/put-item db-opts (table-name db-opts) dyn-new-item)))
  true)

(comment

  "Example data:

   bounced_email:
    [{:email iacopo@bounceme.com
      :created_at 2021-08-24T03:38:12.321Z
      :updated_at 2021-08-24T03:38:12.120Z
      :resource-type hard-bounce
      :bounce_count 1}

     {:email iacopo@bounceme.com
      :created_at 2021-08-22T03:38:02.231Z
      :updated_at 2021-08-23T05:12:12.052Z
      :resource-type hard-bounce
      :bounce_count 3}]
  "

  (require '[oc.lib.email.resources.bounced-email :as bounced-email] :reload)

  (far/list-tables db-opts)

  (far/delete-table db-opts (bounced-email/table-name db-opts))
  (aprint
   (far/create-table db-opts
     (bounced-email/table-name db-opts)
     [:email :s]
     {:range-keydef [:resource_type :s]
      :billing-mode :pay-per-request
      :block? true}))

  (aprint
   @(far/update-table db-opts
     (bounced-email/table-name db-opts)
     {:gsindexes {:operation :create
                  :name (bounced-email/resource-type-gsi-name db-opts)
                  :billing-mode :pay-per-request
                  :hash-keydef [:resource-type :s]
                  :range-keydef [:email :s]
                  :projection :keys-only}}))

  ;; Board followers

  (far/delete-table db-opts (follow/board-unfollower-table-name db-opts))
  (aprint
   (far/create-table db-opts
     (follow/board-unfollower-table-name db-opts)
     [:unfollow_board_uuid :s]
     {:range-keydef [:org_slug :s]
      :billing-mode :pay-per-request
      :block? true}))

  (aprint (far/describe-table db-opts (follow/table-name db-opts)))

  (follow/store! db-opts "1111-1111-1111" "aaaa-aaaa-aaaa" ["3333-3333-3333"] ["2222-2222-2222"])

  (follow/follow! db-opts "1111-1111-1111" "aaaa-aaaa-aaaa" "4444-4444-4444")

  (follow/unfollow! db-opts "1111-1111-1111" "aaaa-aaaa-aaaa" "2222-2222-2222")

  (follow/retrieve db-opts "1111-1111-1111" "aaaa-aaaa-aaaa")
  (follow/retrieve-all db-opts "aaaa-aaaa-aaaa")

  (follow/delete! db-opts "1111-1111-1111" "aaaa-aaaa-aaaa")

  (follow/delete-by-org! db-opts "aaaa-aaaa-aaaa"))