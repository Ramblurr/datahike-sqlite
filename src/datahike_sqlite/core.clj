;; Copyright © 2020 lambdaforge UG (haftungsbeschränkt)
;; Copyright © 2025 Outskirts Labs e.U.
;; This program and the accompanying materials are made available under the
;; terms of the Eclipse Public License 1.0 which is available at
;; http://www.eclipse.org/legal/epl-1.0
;;
;; SPDX-License-Identifier: EPL-1.0
;;
;; This program is extracted from the more general datahike-jdbc
;; https://github.com/replikativ/datahike-jdbc/
(ns datahike-sqlite.core
  (:require [datahike.store :refer [empty-store delete-store connect-store default-config config-spec release-store store-identity]]
            [datahike.config :refer [map-from-env]]
            [datahike-sqlite.konserve :as k]
            [clojure.spec.alpha :as s]))

(defmethod store-identity :sqlite [store-config]
  ;; the store signature is made up of the dbtype, dbname, and table
  (let [{:keys [dbtype dbname table]} store-config]
    [:sqlite dbtype dbname table]))

(defmethod empty-store :sqlite [store-config]
  (k/connect-store store-config))

(defmethod delete-store :sqlite [store-config]
  (k/delete-store store-config))

(defmethod connect-store :sqlite [store-config]
  (k/connect-store store-config))

(defmethod default-config :sqlite [config]
  ;; with the introduction of the store-identity config data should derived from inputs and not set to default values
  (let [env-config (map-from-env :datahike-store-config {})
        passed-config config]
    (merge env-config passed-config)))

(s/def :datahike.store.sqlite/backend #{:sqlite})
(s/def :datahike.store.sqlite/dbtype #{"sqlite"})
(s/def :datahike.store.sqlite/dbname string?)
(s/def :datahike.store.sqlite/classname string?)
(s/def :datahike.store.sqlite/table string?)
(s/def :datahike.store.sqlite/sqlite-opts map?)

(s/def ::sqlite (s/keys :req-un [:datahike.store.sqlite/backend
                                 :datahike.store.sqlite/dbname]
                        :opt-un [:datahike.store.sqlite/dbtype
                                 :datahike.store.sqlite/classname
                                 :datahike.store.sqlite/table
                                 :datahike.store.sqlite/sqlite-opts]))

(defmethod config-spec :sqlite [_] ::sqlite)

(defmethod release-store :sqlite [_ store]
  (k/release store {:sync? true}))
