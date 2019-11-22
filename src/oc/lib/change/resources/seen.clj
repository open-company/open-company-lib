(ns oc.lib.change.resources.seen
  "Retrieve tuples from seen table of Change service"
  (:require [taoensso.faraday :as far]
            [schema.core :as schema]
            [oc.lib.schema :as lib-schema]))

(defn table-name [db-opts]
  (keyword (str (:table-prefix db-opts) "_seen")))

(schema/defn ^:always-validate retrieve :- [{:container-id lib-schema/UniqueID :item-id lib-schema/UniqueID :seen-at lib-schema/ISO8601}]
  [dynamodb-opts user-id :- lib-schema/UniqueID]
  (->> (far/query dynamodb-opts (table-name dynamodb-opts) {:user_id [:eq user-id]})
      (map #(clojure.set/rename-keys % {:container_id :container-id :item-id :item-id :seen_at :seen-at}))
      (map #(select-keys % [:container-id :item-id :seen-at]))))
