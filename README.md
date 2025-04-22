# Datahike SQLite Backend

This library provides a backend for [Datahike][datahike] using [SQLite][sqlite] as the backing store.

**Why not [datahike-jdbc]?** SQLite is different than other JDBC supported databases. The biggest difference is that we do not need connection pooling and the complexity that brings.

## Usage

Include the library in your deps.edn, along with the peer dependencies.

``` clojure
 org.xerial/sqlite-jdbc   {:mvn/version "3.49.1.0"}
 io.replikativ/datahike   {:mvn/version "0.6.1596"}
 ramblurr/datahike-sqlite {:git/url "https://github.com/ramblurr/datahike-sqlite"
                           :git/sha "0626e8cdab400f895f8c2fba3ada108263107ea8"}
```

Then use it like so:

``` clojure
(require
 '[datahike-sqlite.core] ;; required to pull in the multi-method implementations
 '[datahike.api :as d])

(def cfg {:store {:backend :sqlite
                  :journal_mode "WAL"
                  :synchronous  "NORMAL"
                  :dbname  "mydb.sqlite"}})

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
```

## Configuration

The value for `:store` is a db-spec map. To invoke this library you should
include `:backend :sqlite` in that map, along with other sqlite/jdbc specific
options.

Please read the [Datahike configuration docs][setup-docs] on how to configure your backend. 

SQLite specific options can be passed in this map for example `:journal_mode "WAL"`, you can find more examples in the [`org.sqlite.SQLiteConfig`][sqlite-config].

               

## Run Tests

```bash
  bash -x ./bin/run-integration-tests
```

## License

```
Copyright © 2020 lambdaforge UG (haftungsbeschränkt)
Copyright © 2021-2022 Judith Massa, Alexander Oloo
Copyright © 2025 Outskirts Labs e.U.

This program and the accompanying materials are made available under the terms of the Eclipse Public License 2.0.
```

[sqlite]: https://www.sqlite.org
[datahike]: https://github.com/replikativ/datahike
[datahike-jdbc]: https://github.com/replikativ/datahike-jdbc/
[setup-docs]: https://github.com/replikativ/datahike/blob/master/doc/config.md
[sqlite-config]: https://github.com/xerial/sqlite-jdbc/blob/639e362f97e10a55db772009d17b7e456675d37f/src/main/java/org/sqlite/SQLiteConfig.java#L382
