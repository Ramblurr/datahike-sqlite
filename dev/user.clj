;; Copyright © 2025 Casey Link <casey@outskirtslabs.com>
;; SPDX-License-Identifier: MIT
(ns user
  (:require
   [datahike-sqlite.core]
   [datahike.api :as d]))

(comment
  (clojure.repl.deps/sync-deps)

  (def cfg {:store {:backend :sqlite
                    :dbname  "for-the-ceo.sqlite"}})

  (d/database-exists? cfg)
  ;; => false

  (d/create-database cfg)

  ;; this will delete the table in the sqlite file,
  ;; but will not delete the sqlite file itself
  (d/delete-database cfg)

  (def conn (d/connect cfg))

  (d/transact conn [{:db/ident       :name
                     :db/valueType   :db.type/string
                     :db/cardinality :db.cardinality/one}
                    {:db/ident       :age
                     :db/valueType   :db.type/long
                     :db/cardinality :db.cardinality/one}])

  (d/transact conn [{:db/id 1, :name "Ivan", :age 15}])

  (d/release conn)
  1
  ;;
  )

(comment
  (def cfg {:store              {:backend      :sqlite
                                 :dbname       "user.sqlite"
                                 :table        "konserve"
                                 :sqlite-opts {:pool-size 4
                                               :read-only false
                                               :pragma {:foreign_keys false}}}
            :schema-flexibility :write
            :keep-history?      false})
  (d/create-database cfg)
  (d/delete-database cfg)

  (def conn (d/connect cfg))

  (d/transact conn [{:db/ident       :name
                     :db/valueType   :db.type/string
                     :db/cardinality :db.cardinality/one}
                    {:db/ident       :age
                     :db/valueType   :db.type/long
                     :db/cardinality :db.cardinality/one}])

  (d/transact conn [{:db/id 1, :name "Ivan", :age 15}])
  (d/q '[:find (pull ?e [*])
         :where [?e :name _]] @conn)

  (d/release conn)

  ;;
  )
