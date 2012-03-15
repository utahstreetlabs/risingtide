(ns risingtide.core
  (:use [robert.bruce :only [try-try-again]])
  (:require [accession.core :as redis]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

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
  (redis/with-connection (redis/connection-map {}) (redis/lpush "resque:queue:stories" "{\"class\":\"Stories::RemoveInterestInActor\",\"args\":[47,634],\"context\":{\"log_weasel_id\":\"BROOKLYN-WEB-aef04660348f5f018d1f\"}}"))
 )
