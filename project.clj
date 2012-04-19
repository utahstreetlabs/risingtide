(load-file "src/risingtide/version.clj")

(defproject risingtide risingtide.version/version
  :description "He who reads the stories writes the feeds."
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/data.json "0.1.1"]
                 [org.clojure/tools.logging "0.2.3"]

                 [clj-time "0.3.4"]
                 [clj-logging-config "1.9.6"]
                 [enlive "1.0.0"]
                 [ring/ring-core "1.0.2"]
                 [ring/ring-jetty-adapter "1.0.2"]
                 [robert/bruce "0.7.1"]
                 [utahstreetlabs/accession "0.1.2-usl3" :exclusions [org.clojure/clojure]]

                 [mycroft/mycroft "0.0.2"]]

  :dev-dependencies [[midje "1.3.1"]
                     [lein-midje "1.0.8"]]
  :repositories {"usl-releases" "s3p://utahstreetlabs-maven/releases/"
                 "usl-snapshots" "s3p://utahstreetlabs-maven/snapshots/"}
  :main risingtide
  :run-aliases {:convert-redis-keys-from-staging-to-dev! risingtide.utils/run-convert-redis-keys-from-staging-to-dev!
                :build-watcher-indexes! risingtide.utils/run-build-watcher-indexes!
                :check-interest-coherence risingtide.utils/run-check-interest-coherence
                :check-watcher-coherence risingtide.utils/run-check-watcher-coherence
                :build-feeds! risingtide.utils/build-feeds!
                :update-story-feed-params! risingtide.utils/update-story-feed-params!}
  :jvm-opts ["-Dcom.sun.management.jmxremote.port=4056" "-Dcom.sun.management.jmxremote.authenticate=false" "-Dcom.sun.management.jmxremote.ssl=false"])
