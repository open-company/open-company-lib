(ns oc.lib.db.common
  "CRUD functions on resources stored in RethinkDB."
  (:require [clojure.string :as s]
            [clojure.core.async :as async]
            [defun.core :refer (defun defun-)]
            [rethinkdb.query :as r]
            [oc.lib.schema :as lib-schema]
            [oc.lib.time :as oc-time]))

;; ----- ISO 8601 timestamp -----

(def timestamp-format oc-time/timestamp-format)

(def current-timestamp oc-time/current-timestamp)

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

(defn- drain-cursor
  "If the result is a cursor, drain it into a Clojure sequence."
  [result]
  (if (= (type result) rethinkdb.net.Cursor)
    (seq result)
    result))

(defn- iterate-filters
  "Given a list of filters, map the list into RethinkDB functions."
  [filter-map row]
  (for [filter filter-map]
    (cond

      (= :contains (:fn filter))
      (r/contains (:value filter) (r/get-field row (:field filter)))

      (= :ne (:fn filter))
      (r/ne (:value filter) (r/get-field row (:field filter)))

      (= :eq (:fn filter))
      (r/eq (:value filter) (r/get-field row (:field filter)))

      ;; NB: For the following four, we reverse the expectation of what is the left-hand and right-hand 
      ;; side of the comparison, so we provide the opposite filter from what has been asked for

      (= :le (:fn filter))
      (r/ge (:value filter) (r/get-field row (:field filter)))

      (= :lt (:fn filter))
      (r/gt (:value filter) (r/get-field row (:field filter)))

      (= :ge (:fn filter))
      (r/le (:value filter) (r/get-field row (:field filter)))

      (= :gt (:fn filter))
      (r/lt (:value filter) (r/get-field row (:field filter))))))

(defn build-filter-fn
  [filter-map]
  (r/fn [row]
        (if (> (count filter-map) 1)
          (apply r/and (iterate-filters filter-map row))
          (first (iterate-filters filter-map row)))))

;; ----- DB Access Timeouts ----

(def default-timeout 50000) ; 50 sec

(defmacro with-timeout
  "A basic macro to wrap things in a timeout.
  Will throw an exception if the operation times out.
  Note: This is a simplistic approach and piggybacks on core.asyncs executor-pool.
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
  "
  Given a table name and a primary key value, retrieve the resource from the database,
  or return nil if it doesn't exist.
  "
  [conn table-name primary-key-value]
  {:pre [(conn? conn)
         (s-or-k? table-name)]}
  (-> (r/table table-name)
      (r/get primary-key-value)
      (r/run conn)))

(defn read-resources
  "
  Given a table name, and an optional index name and value, an optional set of fields, and an optional limit, retrieve
  the resources from the database.
  "
  ([conn table-name]
  {:pre [(conn? conn)
         (s-or-k? table-name)]}
  (with-timeout default-timeout
    (-> (r/table table-name)
        (r/run conn)
        (drain-cursor))))

  ([conn table-name fields]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (sequential? fields)
         (every? s-or-k? fields)]}
  (with-timeout default-timeout
    (-> (r/table table-name)
        (r/with-fields fields)
        (r/run conn)
        (drain-cursor))))

  ([conn table-name index-name index-value]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (s-or-k? index-name)
         (or (s-or-k? index-value) (sequential? index-value))]}
  (let [index-values (if (sequential? index-value) index-value [index-value])]
    (with-timeout default-timeout
       (-> (r/table table-name)
          (r/get-all index-values {:index index-name})
          (r/run conn)
          (drain-cursor)))))

  ([conn table-name index-name index-value fields]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (s-or-k? index-name)
         (or (s-or-k? index-value) (sequential? index-value))
         (sequential? fields)
         (every? s-or-k? fields)]}
  (let [index-values (if (sequential? index-value) index-value [index-value])]
    (with-timeout default-timeout
      (-> (r/table table-name)
          (r/get-all index-values {:index index-name})
          (r/pluck fields)
          (r/run conn)
          (drain-cursor)))))

  ([conn table-name index-name index-value fields limit]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (s-or-k? index-name)
         (or (s-or-k? index-value) (sequential? index-value))
         (sequential? fields)
         (every? s-or-k? fields)
         (number? limit)]}
  (let [index-values (if (sequential? index-value) index-value [index-value])]
    (with-timeout default-timeout
      (-> (r/table table-name)
          (r/get-all index-values {:index index-name})
          (r/limit limit)
          (r/pluck fields)
          (r/run conn)
          (drain-cursor))))))

(defn filter-resources
  "
  Given a table name, and a filter map in the form of:

  - a `filter-map` A sequence of filters used to filter results.
    example: [{:fn :ne :value 'blah' :field 'foo'}
              {:fn :eq :value true :field 'bar'}]

  Return the resources which pass the provided filter(s).

  Valid filters are: :eq, :ne, :gt, :ge, :lt, :le

  All filters are AND'ed together if more than one is provided.
  "
  ([conn table-name filter-map]
  {:pre [(conn? conn)
     (s-or-k? table-name)
     (sequential? filter-map)]}
  (with-timeout default-timeout
    (-> (r/table table-name)
        (r/filter (build-filter-fn filter-map))
        (r/run conn)
        (drain-cursor))))

  ([conn table-name filter-map fields]
  {:pre [(conn? conn)
     (s-or-k? table-name)
     (sequential? filter-map)
     (sequential? fields)]}
    (with-timeout default-timeout
      (-> (r/table table-name)
          (r/filter (build-filter-fn filter-map))
          (r/pluck fields)
          (r/run conn)
          (drain-cursor)))))

(defn read-resources-and-relations
  "
  In the first arity (9): Given a table name, an index name and value, and what amounts to a document key, foreign table,
  foreign key, foreign key index and the fields of the foreign table that are interesting, return all the resources
  that match the index, and any related resources in the other table in an array in each resource.

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

  The second arity (14) is largely the same functionality as the first, but with more control over the selection and
  order of the returned resources in the form of:

  - an `order-by` field for the order of the returned resources
  - an `order`, one of either `:desc` or `:asc`
  - an initial value of the order-by field to `start` the limited set from
  - a `direction`, one of either `:before` or `:after` the `start` value
  - `limit`, a numeric limit to the number returned

  The third arity (15) is largely the same functionality as the second, but with an additonal filter map
  in the form of:

  - a `filter-map` A sequence of filters used to filter results.
    example: [{:fn :ne :value 'blah' :field 'foo'}
              {:fn :eq :value true :field 'bar'}]

  Valid filters are: :eq, :ne, :gt, :ge, :lt, :le

  All filters are AND'ed together if more than one is provided.
  "
  ;; TODO: Switch to a query map for index, relationship, filtering, and ordering.
  ([conn table-name index-name index-value
   relation-name relation-table-name relation-field-name relation-index-name relation-fields {:keys [count] :or {count false}}]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))
         (s-or-k? relation-name)
         (s-or-k? relation-table-name)
         (s-or-k? relation-field-name)
         (s-or-k? relation-index-name)
         (sequential? relation-fields)
         (every? s-or-k? relation-fields)]}
  (let [index-values (if (sequential? index-value) index-value [index-value])]
    (with-timeout default-timeout
      (as-> (r/table table-name) query
          (r/get-all query index-values {:index index-name})
          (if-not count (r/merge query (r/fn [resource]
            {relation-name (-> (r/table relation-table-name)
                               (r/get-all [(r/get-field resource relation-field-name)] {:index relation-index-name})
                               (r/pluck relation-fields)
                               (r/coerce-to :array))}))
                  query)
          (if count (r/count query) query)
          (r/run query conn)
          (drain-cursor query)))))

  ([conn table-name index-name index-value
    order-by order start direction
    relation-name relation-table-name relation-field-name relation-index-name relation-fields {:keys [count] :or {count false}}]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))
         (s-or-k? order-by)
         (#{:desc :asc} order)
         (not (nil? start))
         (#{:before :after} direction)
         (s-or-k? relation-name)
         (s-or-k? relation-table-name)
         (s-or-k? relation-field-name)
         (s-or-k? relation-index-name)
         (sequential? relation-fields)
         (every? s-or-k? relation-fields)]}
  (let [index-values (if (sequential? index-value) index-value [index-value])
        order-fn (if (= order :desc) r/desc r/asc)
        filter-fn (if (= direction :before) r/gt r/lt)]
    (with-timeout default-timeout
      (as-> (r/table table-name) query
          (r/get-all query index-values {:index index-name})
          (r/filter query (r/fn [row]
                      (filter-fn start (r/get-field row order-by))))
          (if-not count (r/order-by query (order-fn order-by)) query)
          (if-not count (r/merge query (r/fn [resource]
            {relation-name (-> (r/table relation-table-name)
                               (r/get-all [(r/get-field resource relation-field-name)] {:index relation-index-name})
                               (r/pluck relation-fields)
                               (r/coerce-to :array))}))
                  query)
          (if count (r/count query) query)
          (r/run query conn)
          (drain-cursor query)))))

  ([conn table-name index-name index-value
    order-by order start direction limit
    relation-name relation-table-name relation-field-name relation-index-name relation-fields {:keys [count] :or {count false}}]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))
         (s-or-k? order-by)
         (#{:desc :asc} order)
         (not (nil? start))
         (#{:before :after} direction)
         (number? limit)
         (s-or-k? relation-name)
         (s-or-k? relation-table-name)
         (s-or-k? relation-field-name)
         (s-or-k? relation-index-name)
         (sequential? relation-fields)
         (every? s-or-k? relation-fields)]}
  (let [index-values (if (sequential? index-value) index-value [index-value])
        order-fn (if (= order :desc) r/desc r/asc)
        filter-fn (if (= direction :before) r/gt r/lt)]
    (with-timeout default-timeout
      (as-> (r/table table-name) query
          (r/get-all query index-values {:index index-name})
          (r/filter query (r/fn [row]
                      (filter-fn start (r/get-field row order-by))))
          (if-not count (r/order-by query (order-fn order-by)) query)
          (if-not count (r/limit query limit) query)
          (if-not count (r/merge query (r/fn [resource]
            {relation-name (-> (r/table relation-table-name)
                               (r/get-all [(r/get-field resource relation-field-name)] {:index relation-index-name})
                               (r/pluck relation-fields)
                               (r/coerce-to :array))}))
                  query)
          (if count (r/count query) query)
          (r/run query conn)
          (drain-cursor query)))))

  ([conn table-name index-name index-value
    order-by order start direction limit
    filter-map
    relation-name relation-table-name
    relation-field-name relation-index-name relation-fields {:keys [count] :or {count false}}]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))
         (s-or-k? order-by)
         (#{:desc :asc} order)
         (not (nil? start))
         (#{:before :after} direction)
         (number? limit)
         (sequential? filter-map)
         (s-or-k? relation-name)
         (s-or-k? relation-table-name)
         (s-or-k? relation-field-name)
         (s-or-k? relation-index-name)
         (sequential? relation-fields)
         (every? s-or-k? relation-fields)]}
  (let [index-values (if (sequential? index-value) index-value [index-value])
        order-fn (if (= order :desc) r/desc r/asc)
        filter-fn (if (= direction :before) r/gt r/lt)
        filter-by-fn (build-filter-fn filter-map)]
    (with-timeout default-timeout
      (as-> (r/table table-name) query
            (r/get-all query index-values {:index index-name})
            (r/filter query filter-by-fn)
            (r/filter query (r/fn [row]
                                  (filter-fn start (r/get-field row order-by))))
            (if-not count (r/order-by query (order-fn order-by)) query)
            (if-not count (r/limit query limit) query)
            (if-not count (r/merge query (r/fn [resource]
              {relation-name (-> (r/table relation-table-name)
                                 (r/get-all [(r/get-field resource relation-field-name)] {:index relation-index-name})
                                 (r/pluck relation-fields)
                                 (r/coerce-to :array))}))
                    query)
            (if count (r/count query) query)
            (r/run query conn)
            (drain-cursor query))))))

(defn read-all-resources-and-relations
  "Like `read-resources-and-relations` but doesn't apply limit."
  ([conn table-name index-name index-value
    order-by order start direction
    relation-name relation-table-name
    relation-field-name relation-index-name relation-fields {:keys [count] :or {count false}}]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))
         (s-or-k? order-by)
         (#{:desc :asc} order)
         (not (nil? start))
         (#{:before :after} direction)
         (s-or-k? relation-name)
         (s-or-k? relation-table-name)
         (s-or-k? relation-field-name)
         (s-or-k? relation-index-name)
         (sequential? relation-fields)
         (every? s-or-k? relation-fields)]}
  (let [index-values (if (sequential? index-value) index-value [index-value])
        order-fn (if (= order :desc) r/desc r/asc)
        filter-fn (if (= direction :before) r/gt r/lt)]
    (with-timeout default-timeout
      (as-> (r/table table-name) query
            (r/get-all query index-values {:index index-name})
            (r/filter query (r/fn [row]
                                  (filter-fn start (r/get-field row order-by))))
            (if-not count (r/order-by query (order-fn order-by)) query)
            (if-not count (r/merge query (r/fn [resource]
              {relation-name (-> (r/table relation-table-name)
                                 (r/get-all [(r/get-field resource relation-field-name)] {:index relation-index-name})
                                 (r/pluck relation-fields)
                                 (r/coerce-to :array))}))
                    query)
            (if count (r/count query) query)
            (r/run query conn)
            (drain-cursor query)))))
  ([conn table-name index-name index-value
    order-by order start direction
    filter-map
    relation-name relation-table-name
    relation-field-name relation-index-name relation-fields {:keys [count] :or {count false}}]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))
         (s-or-k? order-by)
         (#{:desc :asc} order)
         (not (nil? start))
         (#{:before :after} direction)
         (sequential? filter-map)
         (s-or-k? relation-name)
         (s-or-k? relation-table-name)
         (s-or-k? relation-field-name)
         (s-or-k? relation-index-name)
         (sequential? relation-fields)
         (every? s-or-k? relation-fields)]}
  (let [index-values (if (sequential? index-value) index-value [index-value])
        order-fn (if (= order :desc) r/desc r/asc)
        filter-fn (if (= direction :before) r/gt r/lt)
        filter-by-fn (build-filter-fn filter-map)]
    (with-timeout default-timeout
      (as-> (r/table table-name) query
            (r/get-all query index-values {:index index-name})
            (r/filter query filter-by-fn)
            (r/filter query (r/fn [row]
                                  (filter-fn start (r/get-field row order-by))))
            (if-not count (r/order-by query (order-fn order-by)) query)
            (if-not count (r/merge query (r/fn [resource]
              {relation-name (-> (r/table relation-table-name)
                                 (r/get-all [(r/get-field resource relation-field-name)] {:index relation-index-name})
                                 (r/pluck relation-fields)
                                 (r/coerce-to :array))}))
                    query)
            (if count (r/count query) query)
            (r/run query conn)
            (drain-cursor query))))))

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
        (r/run conn)
        (drain-cursor))))

  ([conn table-name primary-keys fields]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (sequential? primary-keys)
         (every? string? primary-keys)
         (sequential? fields)
         (every? s-or-k? fields)]}
  (with-timeout default-timeout
    (-> (r/table table-name)
        (r/get-all primary-keys)
        (r/pluck fields)
        (r/run conn)
        (drain-cursor)))))

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

(defn grouped-resources-by-most-common
  "
  Given a table name, an index name and value, a grouping field, return an sequence of the grouping field,
  and a count of how many were in the group. Sequence is ordered, most common to least. Optionally specify
  a limit on how many to return.

  Response:

  [['😜' 3] ['👌' 2] ['💥' 1]]
  "
  ([conn table-name index-name index-value group-field]
  (grouped-resources-by-most-common conn table-name index-name index-value group-field nil))
  
  ([conn table-name index-name index-value group-field limit]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))
         (or (nil? limit) (integer? limit))]}
  (let [index-values (if (sequential? index-value) index-value [index-value])
        resource-counts (with-timeout default-timeout
                          (-> (r/table table-name)
                              (r/get-all index-values {:index index-name})
                              (r/with-fields [group-field])
                              (r/group group-field)
                              (r/map (r/fn [value] 1))
                              (r/reduce (r/fn [l r] (r/add l r)))
                              (r/run conn)
                              (drain-cursor)))
        sorted-resources (reverse (sort #(compare (resource-counts %1)
                                                  (resource-counts %2))
                                    (keys resource-counts)))
        limited-resources (if limit (take limit sorted-resources) sorted-resources)]
    (vec (map #(vec [% (resource-counts %)]) limited-resources)))))

(defn months-with-resource
  "
  Given a table name, an index name and value, and an ISO8601 date field, return an ordered sequence of all the months 
  that have at least one resource.

  Response:

  [['2017' '06'] ['2017' '01'] [2016 '05']]

  Sequence is ordered, newest to oldest.
  "
  [conn table-name index-name index-value date-field]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (s-or-k? index-name)
         (or (string? index-value) (sequential? index-value))]}
  (let [index-values (if (sequential? index-value) index-value [index-value])]
    (reverse (sort-by #(str (first %) "-" (last %))
      (with-timeout default-timeout
        (-> (r/table table-name)
            (r/get-all index-values {:index index-name})
            (r/get-field date-field)
            (r/map (r/fn [value] (r/limit (r/split value "-" 2) 2))) ; only the first 2 parts of the ISO8601 date
            (r/distinct)
            (r/run conn)
            (drain-cursor)))))))

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
  "
  For the resource specified by the primary key, add the element to the set of elements with the specified field
  name. Return the updated resource if a change is made, and an exception on DB error.
  "
  [conn table-name primary-key-value field element set-operation]
  {:pre [(conn? conn)
         (s-or-k? table-name)
         (s-or-k? field)]}
  (let [field-key (keyword field)
        ts (current-timestamp)
        update (with-timeout default-timeout
                  (-> (r/table table-name)
                      (r/get primary-key-value)
                      (r/update (r/fn [document]
                        {:updated-at ts field-key (-> (r/get-field document field-key)(set-operation element))}))
                      (r/run conn)))]
    (if (or (= 1 (:replaced update)) (= 1 (:unchanged update)))
      (read-resource conn table-name primary-key-value)
      (throw (RuntimeException. (str "RethinkDB update failure: " update))))))

(defn add-to-set
  "
  For the resource specified by the primary key, add the element to the set of elements with the specified field
  name. Return the updated resource if a change is made, and an exception on DB error.
  "
  [conn table-name primary-key-value field element]
  (update-set conn table-name primary-key-value field element r/set-insert))

(defn remove-from-set
  "
  For the resource specified by the primary key, remove the element to the set of elements with the specified
  field name. Return the updated resource if a change is made, nil if not, and an exception on DB error.
  "
  [conn table-name primary-key-value field element]
  (update-set conn table-name primary-key-value field [element] r/set-difference))

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