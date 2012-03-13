(ns risingtide.core
  (:use [robert.bruce :only [try-try-again]])
  (:require [accession.core :as redis]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [risingtide.key :as key]))

(def rexec redis/with-connection)

(defn pop-or-throw
  [conn key]
  (let [v (rexec conn (redis/lpop key))]
    (if v
      (do (log/info "Processing " v) v)
      (throw (Exception. (str "no value in " key))))))

(defn blocking-pop
  [conn key]
  (try-try-again {:sleep 100 :decay #(min 60000 (* Math/E %)) :tries :unlimited}
                 pop-or-throw conn key))

(defn redis-seq
  "a lazy seq that will read from a redis list

will block indefinitely when attempting to realize an element, polling
redis with exponential backoff until it can pop another element"
  [conn key]
  (lazy-seq (cons (blocking-pop conn key) (redis-seq conn key))))

(comment
  (def d (redis/lpop db "resque:queue:network"))

  (def d (take 1 (redis-seq db "resque:queue:network")))

  (redis/lpop db "resque:queue:stories")

  (rexec c (redis/lpush "resque:queue:stories" "{\"class\":\"Stories::AddInterestInListing\",\"args\":[47,634],\"context\":{\"log_weasel_id\":\"BROOKLYN-WEB-aef04660348f5f018d1f\"}}"))
  (rexec c (redis/lpop "resque:queue:stories"))

  (def d (redis-seq c "resque:queue:stories"))
  (take 3 d)
  (redis/keys db)

  (for [key (redis/keys db)]
   (redis/rename db key (.replaceFirst key "mags" "magd")))
 )
