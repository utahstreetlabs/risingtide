(load-file "src/risingtide/version.clj")

(defproject risingtide risingtide.version/version
  :description "He who reads the stories writes the feeds."
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/data.json "0.1.1"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.clojure/core.memoize "0.5.2"]

                 [utahstreetlabs/jedis "2.1.1"]
                 [copious/domain "1.0.2"]
                 [mysql/mysql-connector-java "5.1.20"]
                 [org.syslog4j/syslog4j "0.9.30"]
                 [net.java.dev.jna/jna "3.4.0"]
                 [clj-time "0.4.4"]

                 [compojure "1.1.0"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [enlive "1.0.0"]

                 [utahstreetlabs/clojure-solr "0.4.0" :exclusions [org.slf4j/slf4j-jcl org.slf4j/slf4j-api]]
                 [risingtide-model "2.3.0"]

                 [metrics-clojure "0.9.2" :exclusions [org.slf4j/slf4j-api]]

                 [storm/carbonite "1.5.0"]
                 [cheshire "5.0.0"]

                 ;; reports
                 [incanter/incanter-core "1.4.0"]
                 [incanter/incanter-charts "1.4.0"]]
  :java-source-paths ["java-src"]
  :profiles {:dev {:dependencies [ [midje "1.5-beta2"]
                                  ;; storm dependency only in dev
                                  ;; cause production storm cluster
                                  ;; provide it
                                  [storm "0.8.1"]
                                  [criterium "0.3.1"]]}}

  :jvm-opts ["-Xmx64m"]
  :aliases {"local-topology" ["trampoline" "run" "-m" "risingtide.storm.local/run!"]}
  :main risingtide
  :min-lein-version "2.0.0"
  :plugins [[lein-midje "2.0.3"]]

  :repositories {"usl-snapshots"
                 {:url "http://ci.copious.com:8082/nexus/content/repositories/snapshots/"
                  :username "deployment" :password "Q5Erm4JqFppGSf"}
                 "usl-releases"
                 {:url "http://ci.copious.com:8082/nexus/content/repositories/releases/"
                  :username "deployment" :password "Q5Erm4JqFppGSf"}}

  :aot [risingtide.serializers.TagLikedStory
        risingtide.serializers.ListingLikedStory
        risingtide.serializers.ListingCommentedStory
        risingtide.serializers.ListingActivatedStory
        risingtide.serializers.ListingSoldStory
        risingtide.serializers.ListingSharedStory
        risingtide.serializers.ListingSavedStory
        risingtide.serializers.MultiActorMultiActionStory
        risingtide.serializers.MultiActorStory
        risingtide.serializers.MultiActionStory
        risingtide.serializers.MultiListingStory
        risingtide.storm.FeedTopology])
