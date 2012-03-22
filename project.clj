(defproject risingtide "1.0.0-SNAPSHOT"
  :description "He who reads the stories writes the feeds."
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/data.json "0.1.1"]
                 [org.clojure/tools.logging "0.2.3"]

                 [utahstreetlabs/accession "0.1.2-usl0" :exclusions [org.clojure/clojure]]
                 [clj-time "0.3.4"]
                 [robert/bruce "0.7.1"]
                 [clj-logging-config "1.9.6"]]
  :dev-dependencies [[midje "1.3.1"]
                     [lein-midje "1.0.8"]]
  :repositories {"usl-releases" "s3p://utahstreetlabs-maven/releases/"
                 "usl-snapshots" "s3p://utahstreetlabs-maven/snapshots/"}
  :main risingtide)

