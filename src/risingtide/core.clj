(ns risingtide.core
  (:require [accession.core :as redis]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]))

(defn now [] (.getMillis (t/now)))

(defn env [] (keyword (or (System/getenv "RISINGTIDE_ENV") "development")))

(defn first-char
  [string-or-keyword]
  (first (name string-or-keyword)))

(defn zunionstore
  "TODO: push this upstream"
  [dest-key source-keys & options]
  (apply redis/query "zunionstore" dest-key
         (count source-keys) (concat source-keys options)))



(comment
  (redis/with-connection (redis/connection-map {}) (redis/lpush "resque:queue:stories" "{\"class\":\"Stories::AddInterestInActor\",\"args\":[47,634],\"context\":{\"log_weasel_id\":\"BROOKLYN-WEB-aef04660348f5f018d1f\"}}"))

(redis/lpush "resque:queue:stories" "hi") =>
"*3\r\n$5\r\nLPUSH\r\n$20\r\nresque:queue:stories\r\n$2\r\nhi\r\n"



(redis/with-connection (redis/connection-map {}) (redis/lpush "resque:queue:stories" "{\"class\":\"Stories::RemoveInterestInActor\",\"args\":[47,634],\"context\":{\"log_weasel_id\":\"BROOKLYN-WEB-aef04660348f5f018d1f\"}}"))
  (redis/with-connection (redis/connection-map {}) (redis/lpop "resque:queue:stories"))

  (redis/with-connection (redis/connection-map {}) (redis/lpush "resque:queue:stories" "{\"class\":\"Stories::Create\",\"args\":[{\"listing_id\":799,\"tag_ids\":[1],\"type\":\"listing_activated\",\"actor_id\":47}],\"context\":{\"log_weasel_id\":\"BROOKLYN-WEB-3114fc3c8086dccb505e\"}}"))


  "{\"class\":\"Stories::AddInterestsInListing\",\"args\":[799],\"context\":{\"log_weasel_id\":\"BROOKLYN-WEB-3114fc3c8086dccb505e\"}}"

  "{\"class\":\"Stories::AddInterestInListing\",\"args\":[47,655],\"context\":{\"log_weasel_id\":\"BROOKLYN-WEB-4124458a124264522a02\"}}"
nil

 )
