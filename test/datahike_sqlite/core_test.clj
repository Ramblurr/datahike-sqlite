;; Copyright © 2020 lambdaforge UG (haftungsbeschränkt)
;; This program and the accompanying materials are made available under the
;; terms of the Eclipse Public License 1.0 which is available at
;; http://www.eclipse.org/legal/epl-1.0
;;
;; SPDX-License-Identifier: EPL-1.0
;;
;; This program is extracted from the more general datahike-jdbc
;; https://github.com/replikativ/datahike-jdbc/
(ns datahike-sqlite.core-test
  (:require
   [clojure.test :as t :refer        [deftest is]]
   [datahike-sqlite.core]
   [datahike.api :as d]))

(deftest ^:integration test-sqlite
  (let [config {:store              {:backend :sqlite
                                     :dbname  "config-test.sqlite"}
                :schema-flexibility :read
                :keep-history?      false}
        _      (d/delete-database config)]
    (is (not (d/database-exists? config)))
    (let [_    (d/create-database config)
          conn (d/connect config)]

      (d/transact conn [{:db/id 1, :name "Ivan", :age 15}
                        {:db/id 2, :name "Petr", :age 37}
                        {:db/id 3, :name "Ivan", :age 37}
                        {:db/id 4, :age 15}])
      (is (= (d/q '[:find ?e :where [?e :name]] @conn)
             #{[3] [2] [1]}))

      (d/release conn)
      (is (d/database-exists? config))
      (d/delete-database config)
      (is (not (d/database-exists? config))))))

(deftest ^:integration test-env
  (let [_ (d/delete-database)]
    (is (not (d/database-exists?)))
    (let [_    (d/create-database)
          conn (d/connect)]

      (d/transact conn [{:db/ident       :name
                         :db/valueType   :db.type/string
                         :db/cardinality :db.cardinality/one}
                        {:db/ident       :age
                         :db/valueType   :db.type/long
                         :db/cardinality :db.cardinality/one}])
      (d/transact conn [{:db/id 1, :name "Ivan", :age 15}
                        {:db/id 2, :name "Petr", :age 37}
                        {:db/id 3, :name "Ivan", :age 37}
                        {:db/id 4, :age 15}])
      (is (= (d/q '[:find ?e :where [?e :name]] @conn)
             #{[3] [2] [1]}))

      (d/release conn)
      (is (d/database-exists?))
      (d/delete-database)
      (is (not (d/database-exists?))))))

(deftest ^:integration test-table
  (let [config  {:store              {:backend :sqlite
                                      :dbname  "config-test.sqlite"}
                 :schema-flexibility :write
                 :keep-history?      false}
        config2 (assoc-in config [:store :table] "tea_party")
        _       (d/delete-database config)
        _       (d/delete-database config2)]
    (is (not (d/database-exists? config)))
    (is (not (d/database-exists? config2)))
    (let [_     (d/create-database config)
          _     (d/create-database config2)
          conn  (d/connect config)
          conn2 (d/connect config2)]
      (d/transact conn [{:db/ident       :name
                         :db/valueType   :db.type/string
                         :db/cardinality :db.cardinality/one}
                        {:db/ident       :age
                         :db/valueType   :db.type/long
                         :db/cardinality :db.cardinality/one}])
      (d/transact conn [{:db/id 1, :name "Ivan", :age 15}
                        {:db/id 2, :name "Petr", :age 37}
                        {:db/id 3, :name "Ivan", :age 37}
                        {:db/id 4, :age 15}])
      (is (= (d/q '[:find ?e :where [?e :name]] @conn)
             #{[3] [2] [1]}))
      (is (empty? (d/q '[:find ?e :where [?e :name]] @conn2)))
      (d/release conn)
      (d/release conn2)
      (is (d/database-exists? config))
      (d/delete-database config)
      (is (d/database-exists? config2))
      (d/delete-database config2)
      (is (not (d/database-exists? config)))
      (is (not (d/database-exists? config2))))))

(deftest ^:integration test-signature
  (let [config  {:store              {:backend :sqlite
                                      :dbname  "config-test.sqlite"
                                      :table   "samezies"}
                 :schema-flexibility :read
                 :keep-history?      false}
        config2 {:store              {:backend :sqlite
                                      :dbname  "config-test.sqlite"
                                      :table   "different"}
                 :schema-flexibility :read
                 :keep-history?      false}]
    (d/delete-database config)
    (d/delete-database config2)
    (is (not (d/database-exists? config)))
    (d/create-database config)
    (d/create-database config2)
    (is (d/database-exists? config))
    (is (d/database-exists? config2))
    (let [conn  (d/connect config)
          conn2 (d/connect config2)]
      (d/transact conn [{:db/id 1, :name "Ivan", :age 15}
                        {:db/id 2, :name "Petr", :age 37}
                        {:db/id 3, :name "Ivan", :age 37}
                        {:db/id 4, :age 15}])
      (is (= (d/q '[:find ?e :where [?e :name]] @conn)
             #{[3] [2] [1]}))

      (is (= (d/q '[:find ?e :where [?e :name]] @conn2)
             #{}))

      (d/release conn)
      (d/release conn2)
      (is (d/database-exists? config))
      (is (d/database-exists? config2))
      (d/delete-database config)
      (d/delete-database config2)
      (is (not (d/database-exists? config)))
      (is (not (d/database-exists? config2))))))

(comment

  {:type          :config-does-not-match-stored-db
   :config        {:keep-history?      false
                   :search-cache-size  10000
                   :index              :datahike.index/persistent-set
                   :store              [:sqlite "sqlite" "config-test.sqlite" nil #uuid "8cb57480-753a-45a5-bc3c-75d23313c932"]
                   :store-cache-size   1000
                   :attribute-refs?    false
                   :crypto-hash?       false
                   :schema-flexibility :read
                   :branch             :db}
   :stored-config {:keep-history?      false
                   :search-cache-size  10000
                   :index              :datahike.index/persistent-set
                   :store              [:sqlite "sqlite" "config-test.sqlite" nil #uuid "d6b8ad70-4a10-4317-a858-595cb82d1e55"]
                   :store-cache-size   1000
                   :attribute-refs?    false
                   :crypto-hash?       false
                   :schema-flexibility :read
                   :branch             :db}
   :diff          ({:store [nil nil nil nil #uuid "8cb57480-753a-45a5-bc3c-75d23313c932"]}
                   {:store [nil nil nil nil #uuid "d6b8ad70-4a10-4317-a858-595cb82d1e55"]}
                   {:keep-history?      false
                    :search-cache-size  10000
                    :index              :datahike.index/persistent-set
                    :store              [:sqlite "sqlite" "config-test.sqlite" nil]
                    :store-cache-size   1000
                    :attribute-refs?    false
                    :crypto-hash?       false
                    :schema-flexibility :read
                    :branch             :db})})
