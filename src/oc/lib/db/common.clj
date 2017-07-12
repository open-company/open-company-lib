(ns oc.lib.db.common
  "CRUD functions on resources stored in RethinkDB."
  (:require [clojure.string :as s]
            [clojure.core.async :as async]
            [if-let.core :refer (if-let*)]
            [defun.core :refer (defun)]
            [clj-time.format :as format]
            [clj-time.core :as time]
            [rethinkdb.query :as r]
            [oc.lib.schema :as lib-schema]))

;; ----- ISO 8601 timestamp -----

(def timestamp-format (format/formatters :date-time))

(defn current-timestamp
  "ISO 8601 string timestamp for the current time."
  []
  (format/unparse timestamp-format (time/now)))

;; ----- Utility functions -----

(defn conn?
  "Check if a var is a valid RethinkDB connection map/atom."
  [conn]
  (lib-schema/conn? conn))

(defn updated-at-order
  "Return items in a sequence sorted by their :updated-at key. Newest first."
  [coll]
  (vec (sort #(compare (:updated-at %2) (:updated-at %1)) coll)))

(defn unique-id
  "Return a 12 character fragment from a UUID e.g. 51ab-4c86-a474"
  []
  (s/join "-" (take 3 (rest (s/split (str (java.util.UUID/randomUUID)) #"-")))))

(defn s-or-k?
  "Truthy if the provided value is a string or a keyword."
  [value]
  (or (string? value)
      (keyword? value)))

;; ----- DB Access Timeouts ----

(def default-timeout 5000) ; 5 sec

(defmacro with-timeout
  "A basic macro to wrap things in a timeout.
  Will throw an exception if the operation times out.
  Note: This is a simplistic approach and piggiebacks on core.asyncs executor-pool.
  Read this discussion for more info: https://gist.github.com/martinklepsch/0caf92b5e42eefa3a894"
  [ms & body]
  `(let [c# (async/thread-call #(do ~@body))]
     (let [[v# ch#] (async/alts!! [c# (async/timeout ~ms)])]
       (if-not (= ch# c#)
         (throw (ex-info "Operation timed out" {}))
         v#))))

;; ----- Resource CRUD -----

(defn create-resource
  "Create a resource in the DB, returning the property map for the resource."
  [conn table-name resource timestamp]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (map? resource)
         (string? timestamp)]}
  (let [timed-resource (merge resource {
          :created-at timestamp
          :updated-at timestamp})
        insert (with-timeout default-timeout
                 (-> (r/table table-name)
                     (r/insert timed-resource)
                     (r/run conn)))]
  (if (= 1 (:inserted insert))
    timed-resource
    (throw (RuntimeException. (str "RethinkDB insert failure: " insert))))))

(defn read-resource
  "Given a table name and a primary key value, retrieve the resource from the database,
  or return nil if it doesn't exist."
  [conn table-name primary-key-value]
  {:pre [(conn? conn)
         (s-or-k? table-name)]}
  (-> (r/table table-name)
      (r/get primary-key-value)
      (r/run conn)))

(defn read-resources
  "Given a table name, and an optional index name and value, and an optional set of fields, retrieve
  the resources from the database."
  ([conn table-name]
  {:pre [(conn? conn)
         (s-or-k? table-name)]}
  (with-timeout default-timeout
    (r/run (r/table table-name) conn)))

  ([conn table-name fields]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (sequential? fields)
         (every? #(or (keyword? %) (string? %)) fields)]}
  (with-timeout default-timeout
    (-> (r/table table-name)
        (r/with-fields fields)
        (r/run conn))))

  ([conn table-name index-name index-value]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))]}
  (let [index-values (if (sequential? index-value) index-value [index-value])]
    (with-timeout default-timeout
       (-> (r/table table-name)
          (r/get-all index-values {:index index-name})
          (r/run conn)))))

  ([conn table-name index-name index-value fields]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))
         (sequential? fields)
         (every? #(or (keyword? %) (string? %)) fields)]}
  (let [index-values (if (sequential? index-value) index-value [index-value])]
    (with-timeout default-timeout
      (-> (r/table table-name)
          (r/get-all index-values {:index index-name})
          (r/pluck fields)
          (r/run conn))))))

(defn read-resources-and-relations
  "Given a table name, an index name and value, and what amounts to a document key, foreign table, foreign key, 
  foreign key index and the fields of the foreign table that are interesting, return all the resources that match
  the index, and any related resources in the other table in an array in each resource.

  E.g.

  Table A: foo, bar
  Table B: blat, a-bar, blu

  Here `bar` and `a-bar` have the same value, with `a-bar` acting as a foreign key pointing each B at an A.

  (read-resources-and-relations conn 'A' :foo-index '1234' :my-bees 'B' :bar :a-bar-index ['blat', 'blu'])

  will return something like:

  [
    {
      :foo '1234'
      :bar 'abcd'
      :my-bees [{:blat 'ferret' :blu 42} {:blat 'monkey' :blu 7}]
    }
    {
      :foo '1234'
      :bar 'efgh'
      :my-bees [{:blat 'mouse' :blu 77} {:blat 'mudskipper' :blu 17}]
    }
  ]
  "
  [conn table-name index-name index-value
   relation-name relation-table-name relation-field-name relation-index-name relation-fields]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))
         (s-or-k? relation-name)
         (s-or-k? relation-table-name)
         (s-or-k? relation-field-name)
         (s-or-k? relation-index-name)
         (sequential? relation-fields)
         (every? #(or (keyword? %) (string? %)) relation-fields)]}
  (let [index-values (if (sequential? index-value) index-value [index-value])]
    (with-timeout default-timeout
      (-> (r/table table-name)
          (r/get-all index-values {:index index-name})
          (r/merge (r/fn [resource]
            {relation-name (-> (r/table relation-table-name)
                               (r/get-all [(r/get-field resource relation-field-name)] {:index relation-index-name})
                               (r/pluck relation-fields)
                               (r/coerce-to :array))}))
          (r/run conn)))))

(defn read-resources-by-primary-keys
  "Given a table name, a sequence of primary keys, and an optional set of fields, retrieve the
  resources from the database."
  ([conn table-name primary-keys]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (sequential? primary-keys)
         (every? string? primary-keys)]}
  (with-timeout default-timeout
    (-> (r/table table-name)
        (r/get-all primary-keys)
        (r/run conn))))

  ([conn table-name primary-keys fields]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (sequential? primary-keys)
         (every? string? primary-keys)
         (sequential? fields)
         (every? #(or (keyword? %) (string? %)) fields)]}
  (with-timeout default-timeout
    (-> (r/table table-name)
        (r/get-all primary-keys)
        (r/pluck fields)
        (r/run conn)))))

(defn read-resources-in-order
  "
  Given a table name, an index name and value, and an optional set of fields, retrieve
  the resources from the database in updated-at property order.
  "
  ([conn table-name index-name index-value]
  {:pre [(conn? conn)]}
  (updated-at-order
    (read-resources conn table-name index-name index-value)))

  ([conn table-name index-name index-value fields]
  {:pre [(conn? conn)]}
  (updated-at-order
    (read-resources conn table-name index-name index-value fields))))

(defun read-resources-in-group
  "
  Given a table name, an index name and value, a field to group resources by, and an optional field to select
  just 1 resource in each group (by max of that field), return the resources in a map with the group field value
  as the key.

  If the optional field is used, there is one resource as the value for each group value in the response map. If the
  optional field is not used, then there is a sequence for each group value in the response map.
  "
  ([conn :guard conn?
    table-name :guard s-or-k?
    index-name :guard s-or-k?
    index-value :guard #(or (s-or-k? %) (and (sequential? %) (every? s-or-k? %)))
    group-by :guard s-or-k?]
  (with-timeout default-timeout
    (-> (r/table table-name)
        (r/get-all [index-value] {:index index-name})
        (r/group group-by)
        (r/run conn))))

  ([conn :guard conn?
    table-name :guard s-or-k?
    index-name :guard s-or-k?
    index-value :guard #(or (s-or-k? %) (and (sequential? %) (every? s-or-k? %)))
    group-by :guard s-or-k?
    select-by :guard #(= % :count)]
  (let [resources (with-timeout default-timeout
                  (-> (r/table table-name)
                    (r/get-all [index-value] {:index index-name})
                    (r/pluck group-by)
                    (r/group group-by)
                    (r/run conn)))
        groups (keys resources)]
    (zipmap groups (map #(count (get resources %)) groups))))

  ([conn :guard conn?
    table-name :guard s-or-k?
    index-name :guard s-or-k?
    index-value :guard #(or (s-or-k? %) (and (sequential? %) (every? s-or-k? %)))
    group-by :guard s-or-k?
    select-by :guard s-or-k?]
  (with-timeout default-timeout
    (-> (r/table table-name)
        (r/get-all [index-value] {:index index-name})
        (r/group group-by)
        (r/max select-by)
        (r/run conn)))))

(defun update-resource
  "
  Given a table name, the name of the primary key, an optional original resource (for efficiency if it's already
  been retrieved) and the updated resource, update the resource in the DB, returning the property map for the
  updated resource.
  "
  ([conn table-name primary-key-name original-resource :guard map? new-resource]
  (update-resource conn table-name primary-key-name original-resource new-resource (current-timestamp)))

  ([conn table-name primary-key-name primary-key-value new-resource]
  (if-let [original-resource (read-resource conn table-name primary-key-value)]
    (update-resource conn table-name primary-key-name original-resource (merge original-resource new-resource))))

  ([conn :guard conn?
    table-name :guard s-or-k?
    primary-key-name :guard s-or-k?
    original-resource :guard map?
    new-resource :guard map?
    timestamp :guard string?]
  (let [timed-resource (merge new-resource {
          primary-key-name (original-resource primary-key-name)
          :created-at (:created-at original-resource)
          :updated-at timestamp})
        update (with-timeout default-timeout
                 (-> (r/table table-name)
                     (r/get (original-resource primary-key-name))
                     (r/replace timed-resource)
                     (r/run conn)))]
  (if (or (= 1 (:replaced update)) (= 1 (:unchanged update)))
    timed-resource
    (throw (RuntimeException. (str "RethinkDB update failure: " update)))))))

(defn remove-property
  "
  Given a table name, the name of the primary key, and a property to remove,
  update a resource in the DB, removing the specified property of the resource.
  "
  ([conn table-name primary-key-value property-name]
  (remove-property conn table-name primary-key-value property-name (current-timestamp)))

  ([conn table-name primary-key-value property-name timestamp]
  {:pre [(conn? conn)]}
  (let [update (with-timeout default-timeout
                  (-> (r/table table-name)
                    (r/get primary-key-value)
                    (r/replace (r/fn [resource]
                      (r/merge
                        (r/without resource [property-name])
                        {:updated-at timestamp})))
                    (r/run conn)))]
    (if (or (= 1 (:replaced update)) (= 1 (:unchanged update)))
      (read-resource conn table-name primary-key-value)
      (throw (RuntimeException. (str "RethinkDB update failure: " update)))))))

(defn delete-resource
  "Delete the specified resource and return `true`."
  ([conn table-name primary-key-value]
  {:pre [(conn? conn)]}
  (let [delete (with-timeout default-timeout
                  (-> (r/table table-name)
                      (r/get primary-key-value)
                      (r/delete)
                      (r/run conn)))]
    (if (= 1 (:deleted delete))
      true
      (throw (RuntimeException. (str "RethinkDB delete failure: " delete))))))

  ([conn table-name key-name key-value]
  {:pre [(conn? conn)]}
  (let [delete (with-timeout default-timeout
                  (-> (r/table table-name)
                      (r/get-all [key-value] {:index key-name})
                      (r/delete)
                      (r/run conn)))]
    (if (zero? (:errors delete))
      true
      (throw (RuntimeException. (str "RethinkDB delete failure: " delete)))))))

(defn delete-all-resources!
  "Use with caution! Failure can result in partial deletes of just some resources. Returns `true` if successful."
  [conn table-name]
  {:pre [(conn? conn)]}
  (let [delete (with-timeout default-timeout
                 (-> (r/table table-name)
                     (r/delete)
                     (r/run conn)))]
    (if (pos? (:errors delete))
      (throw (RuntimeException. (str "RethinkDB delete failure: " delete)))
      true)))

;; ----- Set operations -----

(defn- update-set
  [conn table-name primary-key-value field element set-function]
  {:pre [(conn? conn)]}
  (if-let* [resource (read-resource conn table-name primary-key-value)
            field-key (keyword field)
            initial-value (field-key resource)
            initial-set (if (sequential? initial-value) (set initial-value) #{})
            updated-set (set-function initial-set element)
            not-same? (not= initial-set updated-set) ; short-circuit this if nothing to do
            updated-resource (-> resource
                              (assoc field-key (vec updated-set))
                              (assoc :updated-at (current-timestamp)))]
    (let [update (with-timeout default-timeout
                   (-> (r/table table-name)
                       (r/get primary-key-value)
                       (r/update {field-key updated-set})
                       (r/run conn)))]
      (if (or (= 1 (:replaced update)) (= 1 (:unchanged update)))
        updated-resource
        (throw (RuntimeException. (str "RethinkDB update failure: " update)))))))

;; TODO - maybe desirable to use RethinkDB's set operations: (r/set-insert element)
;; Not yet implemented in clj-rethinkdb
(defn add-to-set
  "
  For the resource specified by the primary key, add the element to the set of elements with the specified field
  name. Return the updated resource if a change is made, nil if not, and an exception on DB error.
  "
  [conn table-name primary-key-value field element]
  (update-set conn table-name primary-key-value field element conj))


;; TODO - maybe desirable to use RethinkDB's set operations: (r/set-difference element)
;; Not yet implemented in clj-rethinkdb
(defn remove-from-set
  "
  For the resource specified by the primary key, remove the element to the set of elements with the specified
  field name. Return the updated resource if a change is made, nil if not, and an exception on DB error.
  "
  [conn table-name primary-key-value field element]
  (update-set conn table-name primary-key-value field element disj))

;; ----- REPL usage -----

(comment

  (require '[rethinkdb.query :as r])
  (require '[oc.lib.db.common :as db-common] :reload)

  (def conn (apply r/connect [:host "127.0.0.1" :port 28015 :db "open_company_storage_dev"]))
  (def conn2 (apply r/connect [:host "127.0.0.1" :port 28015 :db "open_company_auth_dev"]))
  
  (db-common/read-resource conn2 "teams" "c55c-47f1-898e")
  (db-common/add-to-set conn2 "teams" "c55c-47f1-898e" "admins" "1234-1234-1234")
  (db-common/remove-from-set conn2 "teams" "c55c-47f1-898e" "admins" "1234-1234-1234")

  )