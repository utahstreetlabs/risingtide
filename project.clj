(load-file "src/risingtide/version.clj")

(defproject risingtide risingtide.version/version
  :description "He who reads the stories writes the feeds."
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/data.json "0.1.1"]
                 [org.clojure/tools.logging "0.2.3"]

                 [clj-time "0.3.4"]
                 [clj-logging-config "1.9.6"]
                 [compojure "1.1.0"]
                 [ring/ring-jetty-adapter "1.1.0"]
                 [enlive "1.0.0"]
                 [redis.clients/jedis "2.0.0"]
                 [robert/bruce "0.7.1"]
                 [korma "0.3.0-beta11"]
                 [mysql/mysql-connector-java "5.1.20"]]

  :profiles {:dev {:dependencies [[midje "1.4.0"]]}}

  :run-aliases {:convert-redis-keys-from-staging-to-dev! risingtide.utils/run-convert-redis-keys-from-staging-to-dev!}
  :main risingtide
  :min-lein-version "2.0.0"
  :plugins [[lein-midje "2.0.0-SNAPSHOT"]])
