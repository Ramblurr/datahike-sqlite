;; Copyright © 2021-2022 Judith Massa, Alexander Oloo
;; Copyright © 2025 Outskirts Labs e.U.
;; This program and the accompanying materials are made available under the
;; terms of the Eclipse Public License 2.0 which is available at
;; http://www.eclipse.org/legal/epl-2.0
;;
;; SPDX-License-Identifier: EPL-2.0
;;
;; This program is extracted from the more general konserve-jdbc
;; https://github.com/replikativ/konserve-jdbc/
(ns datahike-sqlite.konserve
  (:require
   [konserve.compressor :refer [null-compressor]]
   [konserve.encryptor :refer [null-encryptor]]
   [konserve.impl.defaults :refer [connect-default-store]]
   [konserve.impl.storage-layout :refer [PBackingBlob PBackingLock
                                         PBackingStore -delete-store]]
   [konserve.utils :refer [*default-sync-translation* async+sync]]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [superv.async :refer [go-try-]])
  (:import
   [java.io ByteArrayInputStream]
   [java.sql Connection]))

(set! *warn-on-reflection* 1)

(def ^:const default-table "konserve")

(defn get-connection [db-spec]
  (let [conn     (jdbc/get-connection db-spec)
        shutdown (fn []
                   (.close ^Connection conn))]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable shutdown))
    conn))

(defn extract-bytes [obj]
  (when obj
    obj))

(defn create-statement [table]
  [(str "CREATE TABLE IF NOT EXISTS " table " (id varchar(100) primary key, header bytea, meta bytea, val bytea)")])

(defn update-statement [table id header meta value]
  [(str "INSERT INTO " table " (id, header, meta, val) VALUES (?, ?, ?, ?) "
        "ON CONFLICT (id) DO UPDATE "
        "SET header = excluded.header, meta = excluded.meta, val = excluded.val;")
   id header meta value])

(defn copy-row-statement [table to from]
  [(str "INSERT INTO " table " (id, header, meta, val) "
        "SELECT '" to "', header, meta, val FROM " table "  WHERE id = '" from "' "
        "ON CONFLICT (id) DO UPDATE "
        "SET header = excluded.header, meta = excluded.meta, val = excluded.val;")])

(defn delete-statement [table]
  [(str "DROP TABLE IF EXISTS " table)])

(defn change-row-id [connection table from to]
  (jdbc/execute! connection
                 ["UPDATE " table " SET id = '" to "' WHERE id = '" from "';"]))

(defn read-all [connection table id]
  (let [res (-> (jdbc/execute! connection
                               [(str "SELECT id, header, meta, val FROM " table " WHERE id = '" id "';")]
                               {:builder-fn rs/as-unqualified-lower-maps})
                first)]
    (into {} (for [[k v] res] [k (if (= k :id) v (extract-bytes v))]))))

(extend-protocol PBackingLock
  Boolean
  (-release [_ env]
    (if (:sync? env) nil (go-try- nil))))

(defrecord JDBCRow [table key data cache]
  PBackingBlob
  (-sync [_ env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (let [{:keys [header meta value]} @data]
                           (if (and header meta value)
                             (let [ps (update-statement (:table table) key header meta value)]
                               (jdbc/execute-one! (:connection table) ps))
                             (throw (ex-info "Updating a row is only possible if header, meta and value are set." {:data @data})))
                           (reset! data {})))))
  (-close [_ env]
    (if (:sync? env) nil (go-try- nil)))
  (-get-lock [_ env]
    (if (:sync? env) true (go-try- true)))                       ;; May not return nil, otherwise eternal retries
  (-read-header [_ env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try-
                 (when-not @cache
                   (reset! cache (read-all (:connection table) (:table table) key)))
                 (-> @cache :header))))
  (-read-meta [_ _meta-size env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (-> @cache :meta))))
  (-read-value [_ _meta-size env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (-> @cache :val))))
  (-read-binary [_ _meta-size locked-cb env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (locked-cb {:input-stream (when (-> @cache :val) (ByteArrayInputStream. (-> @cache :val)))
                                     :size         nil}))))
  (-write-header [_ header env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (swap! data assoc :header header))))
  (-write-meta [_ meta env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (swap! data assoc :meta meta))))
  (-write-value [_ value _meta-size env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (swap! data assoc :value value))))
  (-write-binary [_ _meta-size blob env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (swap! data assoc :value blob)))))

(defrecord JDBCTable [db-spec connection table]
  PBackingStore
  (-create-blob [this store-key env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (JDBCRow. this store-key (atom {}) (atom nil)))))
  (-delete-blob [_ store-key env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (jdbc/execute! connection
                                        [(str "DELETE FROM " table " WHERE id = '" store-key "';")]))))
  (-blob-exists? [_ store-key env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (let [res (jdbc/execute! connection
                                                  [(str "SELECT 1 FROM " table " WHERE id = '" store-key "';")])]
                           (not (nil? (first res)))))))
  (-copy [_ from to env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (jdbc/execute! connection (copy-row-statement table to from)))))
  (-atomic-move [_ from to env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (change-row-id connection table from to))))
  (-migratable [_ _key _store-key env]
    (if (:sync? env) nil (go-try- nil)))
  (-migrate [_ _migration-key _key-vec _serializer _read-handlers _write-handlers env]
    (if (:sync? env) nil (go-try- nil)))
  (-create-store [_ env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (jdbc/execute! connection (create-statement table)))))
  (-store-exists? [_ env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (let [res (jdbc/execute! connection [(str "SELECT 1 FROM " table " LIMIT 1")])]
                           (not (nil? res))))))
  (-sync-store [_ env]
    (if (:sync? env) nil (go-try- nil)))
  (-delete-store [_ env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (jdbc/execute! connection (delete-statement table))
                  (.close ^Connection connection))))
  (-keys [_ env]
    (async+sync (:sync? env) *default-sync-translation*
                (go-try- (let [res' (jdbc/execute! connection
                                                   [(str "SELECT id FROM " table ";")]
                                                   {:builder-fn rs/as-unqualified-lower-maps})]
                           (map :id res'))))))

(defn prepare-spec [db-spec opts-table]
  (-> db-spec
      (assoc :dbtype "sqlite")
      (assoc :table (or opts-table (:table db-spec) default-table))))

(defn connect-store [db-spec & {:keys [opts]
                                :as   params}]
  (let [complete-opts (merge {:sync? true} opts)
        db-spec       (-> db-spec (prepare-spec (:table params)) (assoc :sync? (:sync? complete-opts)))

        connection (get-connection db-spec)
        _          (assert (:table db-spec))
        backing    (JDBCTable. db-spec connection (:table db-spec))
        config     (merge {:opts               complete-opts
                           :config             {:sync-blob? true
                                                :in-place?  true
                                                :no-backup? true
                                                :lock-blob? true}
                           :default-serializer :FressianSerializer
                           :compressor         null-compressor
                           :encryptor          null-encryptor
                           :buffer-size        (* 1024 1024)}
                          (dissoc params :opts :config))]
    (connect-default-store backing config)))

(defn release
  "Must be called after work on database has finished in order to close connection"
  [store env]
  (async+sync (:sync? env) *default-sync-translation*
              (go-try-
               (.close ^Connection (:connection ^JDBCTable (:backing store))))))

(defn delete-store [db-spec & {:keys [table opts]}]
  (let [complete-opts (merge {:sync? true} opts)
        db-spec       (prepare-spec db-spec table)
        _             (assert (:table db-spec))]
    (with-open [connection (jdbc/get-connection db-spec)]
      (-delete-store (JDBCTable. db-spec connection (:table db-spec)) complete-opts))))

(comment

  (require '[konserve.core :as k])

  (def db-spec {:dbname "dhsqlite.sqlite" :dbtype "sqlite"})
  (def store (connect-store db-spec :opts {:sync? true}))
  (def store2 (connect-store db-spec :opts {:sync? true}))

  (time (k/assoc-in store ["foo"] {:foo "baz"} {:sync? true}))
  (time (k/assoc-in store ["foo2"] {:foo2 "baz"} {:sync? true}))
  (time (k/assoc-in store2 ["foo"] {:foo "baz"} {:sync? true}))
  (k/get-in store ["foo"] nil {:sync? true})
  (k/exists? store "foo" {:sync? true})

  (k/get store :db nil {:sync? true})

  (k/exists? store2 "foo2" {:sync? true})
  (release store {:sync? true})

  ;;
  )
