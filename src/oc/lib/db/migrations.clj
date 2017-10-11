;; Very loosely patterned after https://github.com/yogthos/migratus
(ns oc.lib.db.migrations
  "Migrate RethinkDB data."
  (:require [clojure.string :as s]
            [clj-time.core :as t]
            [clj-time.coerce :as coerce]
            [rethinkdb.query :as r]
            [oc.lib.db.common :as db-common]
            [oc.lib.slugify :as slug]))

;; ----- Utility functions for migrations -----

(defn- create-database
  "Create a RethinkDB database if it doesn't already exist."
  [conn db-name]
  (if-let [db-list (r/run (r/db-list) conn)]
    (if (some #(= db-name %) db-list)
      true ; already exists, return truthy
      (r/run (r/db-create db-name) conn))))

(defn table-list
  "Return a sequence of the table names in the RethinkDB."
  [conn]
  (r/run (r/table-list) conn))

(defn- migration-file-name [migrations-dir migration-name]
  (str (s/join java.io.File/separator [migrations-dir migration-name]) ".edn"))

(defn- store-migration
  "Keep the migration name in the migration table so we don't run it again."
  [conn migration-name]
  (assert (= 1 (:inserted (-> (r/table "migrations")
                              (r/insert {:name migration-name})
                              (r/run conn))))))

(defn- run-migration
  "Run the migration specified by the migration name."
  [conn migrations-dir migration-name]
  (println "\nRunning migration:" migration-name)
  (let [file-name (migration-file-name migrations-dir migration-name)
        bare-name (s/join "-" (rest (s/split migration-name #"_"))) ; strip the timestamp
        function-ns (s/join "." (-> file-name
                                  (s/replace #"_" "-") ; swap _ for -
                                  (s/split #"\/")
                                  (rest) ; remove ./
                                  (rest) ; remove /src/
                                  (vec)
                                  (drop-last))) ; remove file name portion
        function-name (str function-ns "." bare-name "/up")] ; function name
    (println "Loading name:" file-name)
    (load-file file-name)
    (println "Running function:" function-name)
    ((ns-resolve *ns* (symbol function-name)) conn))) ; run the migration

(defn- run-migrations
  "Given a list of migrations that haven't been run, run them. Abort if any doesn't succeed."
  [conn migrations-dir migration-names]
  (doseq [migration-name migration-names]
    (assert (true? (run-migration conn migrations-dir migration-name)))
    (store-migration conn migration-name))
  :ok)

(defn- report-migrations
  "Report on how many migrations need to be run."
  [migrations]
  (let [m-count (count migrations)]
    (cond 
      (zero? m-count) (println "No new migrations to run.")
      (= 1 m-count) (println "1 new migration to run.")
      :else (println (str m-count " new migrations to run."))))
  migrations)

(defn- new-migrations
  "Given a list of migrations that exist, return just the ones that haven't been run on this DB."
  [conn migrations]
  (let [migration-slugs (set (map #(second (re-matches #".*\/(.*).edn$" %)) (map str migrations))) ; from the filesystem
        existing-slugs (set (map :name (r/run (r/table "migrations") conn)))] ; from the DB
    (sort (clojure.set/difference migration-slugs existing-slugs))))

;; ----- Utility functions for migration helpers -----

(defn- index-list
  "Return a sequence of the index names for a table in the RethinkDB."
  [conn table-name]
  (-> (r/table table-name) (r/index-list) (r/run conn)))

(defn- wait-for-index
  "Pause until an index with the specified name is finished being created."
  [conn table-name index-name]
  (-> (r/table table-name)
    (r/index-wait index-name)
    (r/run conn)))

;; ----- Helper functions for implementing migrations -----

(defn create-index
  "Create RethinkDB table index for the specified field if it doesn't exist."

  ([conn table-name index-name] (create-index conn table-name index-name {}))

  ([conn table-name index-name options]    
  (when (not-any? #(= index-name %) (index-list conn table-name))
    (-> (r/table table-name)
      (r/index-create index-name nil options)
      (r/run conn))
    (wait-for-index conn table-name index-name))))

(defn create-compound-index
  "Create RethinkDB table compound index for the specified fields if it doesn't exist."
  ([conn table-name index-name index-function]
  (create-compound-index conn table-name index-name index-function nil))

  ([conn table-name index-name index-function options]
  (when (not-any? #(= index-name %) (index-list conn table-name))
    (-> (r/table table-name)
      (r/index-create index-name index-function options)
      (r/run conn))
    (wait-for-index conn table-name index-name))))

(defn create-table
  "Create a RethinkDB table with the specified primary key if it doesn't exist."
  [conn db-name table-name primary-key]
  (when (not-any? #(= table-name %) (table-list conn))
    (-> (r/db db-name)
        (r/table-create table-name {:primary-key primary-key :durability "hard"})
        (r/run conn))))

(defn rename-table
  "Rename a RethinkDB table from the old name to the new name if it exists."
  [conn db-name old-table-name new-table-name]
  (let [table-list (table-list conn)]
    (when (and (some #(= old-table-name %) table-list)
               (not-any? #(= new-table-name %) table-list))
      (-> (r/db "rethinkdb")
          (r/table "table_config")
          (r/filter (r/fn [table] {:db db-name :name old-table-name}))
          (r/update {:name new-table-name})
          (r/run conn)))))

(defn delete-table
  "Delete the specified RethinkDB table."
  [conn table-name]
  (r/run (r/table-drop table-name) conn))

(defn remove-properties
  "Remove the specified properties (sequence of strings or keywords)  from all docs in the specified table."
  [conn table-name props]
  {:pre [(sequential? props)
         (every? db-common/s-or-k? props)]}
  (-> (r/table "entries")
      (r/replace (r/fn [resource]
        (r/without resource props)))
      (r/run conn)))

;; ----- Main entry-point functions for creating and running migrations -----

(defn migrate 
  "
  Create the database (if needed) and the migration table (if needed) and run any
  migrations that haven't already been run on this DB.
  "
  ([db-map migrations-dir]
  {:pre [(map? db-map)
         (string? migrations-dir)]}
  (with-open [conn (apply r/connect (flatten (vec db-map)))]
    (migrate conn (:db db-map) migrations-dir)))
  
  ([conn db-name migrations-dir]
  {:pre [(instance? clojure.lang.IDeref conn)
         (string? db-name)
         (string? migrations-dir)]}
  (println (str "\nInitializing database:" db-name))
  ;; create DB (if it doesn't exist)
  (when (create-database conn db-name)
    ;; create migration table (if it doesn't exist)
    (create-table conn db-name "migrations" "name")
    ;; Run the migrations
    (println "\nRunning migrations.")
    (->> (filter #(s/ends-with? % ".edn") (file-seq (clojure.java.io/file migrations-dir)))
         (new-migrations conn)
         report-migrations
         (run-migrations conn migrations-dir))
    (println "Migrations complete."))
  (println "\nDatabase initialization complete.\n")))


(defn create
  "Create a new migration with the current date and the name."
  [migrations-dir migration-template provided-name]
  (let [timestamp (str (coerce/to-long (t/now)))
        migration-name (slug/slugify provided-name 256)
        full-name (str timestamp "-" migration-name)
        file-name (migration-file-name migrations-dir (s/replace full-name #"-" "_"))
        template (slurp migration-template)
        contents (s/replace template #"MIGRATION-NAME" migration-name)]
    (spit file-name contents)))

(comment
  ;; REPL testing

  (require '[rethinkdb.query :as r])
  (require '[oc.lib.db.migrations :as m] :reload)

  (def conn [:host "127.0.0.1" :port 28015 :db "open_company_storage_dev"])

  (m/create "test-it")

  (m/migrate)

  (with-open [c (apply r/connect conn)]
    (m/table-list c))

  (with-open [c (apply r/connect conn)]
    (m/create-table c "foo" "bar"))

  (with-open [c (apply r/connect conn)]
    (m/delete-table c "foo"))
  
  )