# Datahike SQLite Backend

This library provides a backend for [Datahike][datahike] using [SQLite][sqlite] as the backing store, with direct native integration through [sqlite4clj][sqlite4clj].

> [!WARNING]
> This project is highly experimental and not production ready.

## Why sqlite4clj?

Unlike the [datahike-jdbc][datahike-jdbc] backend, this implementation uses [sqlite4clj][sqlite4clj] - a minimalist FFI binding to SQLite's C API using Java 22's Foreign Function Interface (Project Panama). This approach offers several advantages:

- Bypasses JDBC overhead by interfacing directly with SQLite's C API through FFI for direct SQLite access.
- SQLite's embedded nature doesn't require thread-backed connection pools like c3p0/HikariCP, eliminating that complexity.
- Provides better performance through cached prepared statements per connection and inline caching of column reading functions.
- Eliminates dependencies on sqlite-jdbc, c3p0, and next.jdbc for a smaller footprint.
- Easier access to SQLite-specific features and pragmas for targeted optimizations.
- More suitable architecture for SQLite's single-writer, multiple-reader model.

## Usage

Include the library in your deps.edn:

``` clojure
 io.replikativ/datahike   {:mvn/version "0.6.1601"}
 ramblurr/datahike-sqlite {:git/url "https://github.com/ramblurr/datahike-sqlite"
                           :git/sha "c94e449be351b13c7b279d39ee3266cc22dd8f7d"}
```

### Dependencies

- The [io.replikativ/datahike dependency](https://clojars.org/io.replikativ/datahike) must be provided by your project and this backend is designed to work with Datahike 0.6.x versions.
- [sqlite4clj][sqlite4clj] requires Java 22 or later.
- If you use this library as a git dependency, you will need to prepare the library with `clj -X:deps prep`.
- You must include `:jvm-opts ["--enable-native-access=ALL-UNNAMED"]` in your deps.edn alias.
- When creating an executable jar file, you can avoid the need to pass this argument by adding the manifest attribute `Enable-Native-Access: ALL-UNNAMED` to your jar.

### Example

``` clojure
(require
 '[datahike-sqlite.core] ;; required to pull in the multi-method implementations
 '[datahike.api :as d])

(def cfg {:store {:backend :sqlite
                  :dbname  "foobar.sqlite"
                  ;; see sqlite4clj.core/init-db! for the possible options
                  :sqlite-opts {:pool-size 4}}})

(d/database-exists? cfg)
;; => false

(d/create-database cfg)

(def conn (d/connect cfg))

(d/transact conn [{:db/ident       :artifact
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one}
                  {:db/ident       :level
                   :db/valueType   :db.type/long
                   :db/cardinality :db.cardinality/one}])

(d/transact conn [{:name "Mighty Teapot" :age 20}])

(d/q '[:find (pull ?e [*])
       :in $ ?name
       :where [?e :name ?name]]
     @conn "Mighty Teapot")


;; this will delete the table in the sqlite file,
;; but will not delete the sqlite file itself
(d/delete-database cfg)

(d/release conn)
```

## Configuration

The value for `:store` is a configuration map. To invoke datahike-sqlite (this library) you must include `:backend :sqlite` in that map.

You must also include `:dbname`, a path to the SQLite file.

You can optionally include the key `:sqlite-opts` with an options map which will be passed to [`sqlite4clj.core/init-db!`][sqlite4clj].

## Development

### Testing

```bash
bb test
```

### Formatting

```shell
bb fmt
```

### Linting

```shell
bb lint
```

## License: MIT License

Copyright © 2025 Casey Link <casey@outskirtslabs.com> Distributed under the [MIT](https://spdx.org/licenses/MIT.html).

[sqlite]: https://www.sqlite.org
[sqlite4clj]: https://github.com/andersmurphy/sqlite4clj
[datahike]: https://github.com/replikativ/datahike
[datahike-jdbc]: https://github.com/replikativ/datahike-jdbc/
[setup-docs]: https://github.com/replikativ/datahike/blob/master/doc/config.md
[sqlite-config]: https://github.com/xerial/sqlite-jdbc/blob/639e362f97e10a55db772009d17b7e456675d37f/src/main/java/org/sqlite/SQLiteConfig.java#L382
