;; Very loosely patterned after https://github.com/yogthos/migratus
(ns oc.lib.rethinkdb.migrations
  "Migrate RethinkDB data."
  (:require [clojure.string :as s]
            [clj-time.core :as t]
            [clj-time.coerce :as coerce]
            [rethinkdb.query :as r]
            [defun :refer (defun)]
            [oc.lib.slugify :as slug]))

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
  (println "\nRunning migration: " migration-name)
  (let [file-name (migration-file-name migrations-dir migration-name)
        bare-name (s/join "-" (rest (s/split migration-name #"_"))) ; strip the timestamp
        function-name (str "open-company.db.migrations." bare-name "/up")] ; function name
    (println "Loading name: " file-name)
    (load-file file-name)
    (println "Running function: " function-name)
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

(defun migrate 
  "Run any migrations that haven't already been run on this DB."
  ([db-options :guard sequential? migrations-dir] 
  (with-open [conn (apply r/connect db-options)]
    (migrate conn migrations-dir)))
  
  ([conn migrations-dir]
  (->> (rest (file-seq (clojure.java.io/file migrations-dir)))
       (new-migrations conn)
       report-migrations
       (run-migrations conn migrations-dir))))

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