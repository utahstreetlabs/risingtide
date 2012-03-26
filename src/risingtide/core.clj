(ns risingtide.core
  (:require [accession.core :as redis]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

(defn now [] (long (/ (.getTime (java.util.Date.)) 1000)))

(def env-atom (atom nil))

(defn env [] (or @env-atom (keyword (or (System/getenv "RISINGTIDE_ENV") "development"))))

(defn first-char
  [string-or-keyword]
  (first (name string-or-keyword)))

(defn safe-print-stack-trace
  [throwable ns]
  (try
    (.printStackTrace throwable (log/log-stream :error ns))
    (catch Throwable t (log/error "failed to print stack trace with error" t))))

(defmacro bench
  [msg & forms]
  `(let [start# (.getTime (java.util.Date.))
         _# (log/debug "executing" ~msg)
         result# (do ~@forms)]
     (log/info ~msg "in" (- (.getTime (java.util.Date.)) start#))
     result#))

(comment
  (redis/with-connection (redis/connection-map {}) (redis/rpush "resque:queue:stories" "{\"class\":\"Stories::AddInterestInActor\",\"args\":[47,634],\"context\":{\"log_weasel_id\":\"BROOKLYN-WEB-aef04660348f5f018d1f\"}}"))

(redis/with-connection (redis/connection-map {}) (redis/rpush "resque:queue:stories" "{\"class\":\"Stories::RemoveInterestInActor\",\"args\":[47,634],\"context\":{\"log_weasel_id\":\"BROOKLYN-WEB-aef04660348f5f018d1f\"}}"))
  (redis/with-connection (redis/connection-map {}) (redis/lpop "resque:queue:stories"))


(dotimes [n 10000]  (redis/with-connection (redis/connection-map {}) (redis/rpush "resque:queue:stories" (str "{\"class\":\"Stories::Create\",\"args\":[{\"listing_id\":799,\"tag_ids\":[1],\"type\":\"listing_activated\",\"actor_id\":" n "}],\"context\":{\"log_weasel_id\":\"BROOKLYN-WEB-3114fc3c8086dccb505e\"}}"))))

(redis/with-connection (redis/connection-map {}) (redis/rpush "resque:queue:stories" (str "{\"class\":\"Stories::Create\",\"args\":[{\"listing_id\":799,\"tag_ids\":[1],\"type\":\"listing_activated\",\"actor_id\":" 1 "}],\"context\":{\"log_weasel_id\":\"BROOKLYN-WEB-3114fc3c8086dccb505e\"}}")))
(redis/with-connection (redis/connection-map {}) (redis/rpush "resque:queue:stories" (str "{\"class\":\"Stories::Create\",\"args\":[{\"listing_id\":799,\"tag_ids\":[1],\"type\":\"listing_activated\",\"actor_id\":" 2 "}],\"context\":{\"log_weasel_id\":\"BROOKLYN-WEB-3114fc3c8086dccb505e\"}}")))

(redis/with-connection (redis/connection-map {}) (redis/rpush "resque:queue:stories" (str "{\"class\":\"Stories::Create\",\"args\":[{\"type\":\"user_joined\",\"actor_id\":47}],\"context\":{\"log_weasel_id\":\"BROOKLYN-RESQUE-52023d69d3bda45328d5\"}}")))


  "{\"class\":\"Stories::AddInterestsInListing\",\"args\":[799],\"context\":{\"log_weasel_id\":\"BROOKLYN-WEB-3114fc3c8086dccb505e\"}}"

  "{\"class\":\"Stories::AddInterestInListing\",\"args\":[47,655],\"context\":{\"log_weasel_id\":\"BROOKLYN-WEB-4124458a124264522a02\"}}")
