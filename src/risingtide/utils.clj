(ns risingtide.utils
  (:use risingtide.core)
  (:require [accession.core :as redis]
            [risingtide
             [config :as config]
             [feed :as feed]
             [interests :as interests]
             [key :as key]
             [queries :as queries]]))

(defn- env-connection-config
  []
  (redis/connection-map (config/redis (:feeds (env)))))

;; migrate staging keys to development.

(defn convert-redis-keys-from-staging-to-dev!
  ([con]
     (if (= :development (env))
       (apply redis/with-connection con
              (for [key (redis/with-connection con (redis/keys "*"))]
                (redis/rename key (.replaceFirst key "mags" "magd"))))
       (prn "holy shit. you really, really don't want to rename keys in" (env))))
  ([] (convert-redis-keys-from-staging-to-dev! (env-connection-config))))


;; build watcher indexes

(defn build-watcher-indexes!
  ([conn]
     (for [interest-key (redis/with-connection conn (queries/interest-keys))]
       (let [[user-id type] (key/user-id-type-from-interest-key interest-key)]
         (apply redis/with-connection conn
                (for [interest-token (redis/with-connection conn (redis/smembers interest-key))]
                  (redis/sadd (key/watchers interest-token) user-id))))))
  ([] (build-watcher-indexes! (env-connection-config))))

;;;; interest list/watcher list coherence

(defn find-invalid
  [set]
  (filter #(not (second %)) set))

(defn all-ones?
  [set]
  (or (= 1 set)
      (reduce (fn [m v] (and m (= 1 v))) true set)))

(defn check-interest-coherence
  "return any interest keys that don't match the watcher sets"
  ([conn]
     (find-invalid
      (for [interest-key (redis/with-connection (redis/connection-map {}) (queries/interest-keys))]
        (let [[user-id type] (key/user-id-type-from-interest-key interest-key)]
          [interest-key
           (all-ones?
            (apply redis/with-connection conn
                   (for [interest-token (redis/with-connection conn (redis/smembers interest-key))]
                     (redis/sismember (key/watchers interest-token) user-id))))]))))
  ([] (check-interest-coherence (env-connection-config))))

(defn check-watcher-coherence
  "return any watcher keys that don't match the interest sets"
  ([conn]
     (find-invalid
      (for [watchers-key (redis/with-connection (redis/connection-map {}) (queries/watchers-keys))]
        (let [[type object-id] (key/type-object-id-from-watcher-key watchers-key)]
          [watchers-key
           (all-ones?
            (apply redis/with-connection conn
                   (for [user-id (redis/with-connection conn (redis/smembers watchers-key))]
                     (redis/sismember (key/interest user-id type) (interests/interest-token type object-id)))))]))))
  ([] (check-watcher-coherence (env-connection-config))))

;;;; build and truncate a set of feeds

(defn build-feeds!
  ([conn user-ids]
     (apply redis/with-connection conn
      (flatten
       (for [user-id user-ids]
         (concat
          (feed/build-and-truncate-feed conn user-id :card)
          (feed/build-and-truncate-feed conn user-id :network))))))
  ([user-ids-string] (build-feeds! (env-connection-config) (read-string (str "[" user-ids-string "]")))))


;;;; define "runnable jobs" suitable for using with lein run ;;;;
;;
;; right now, this just creates a function name prefixed with run- and
;; ensures agents are shut down after the given forms are executed

(defmacro defrun
  [name & forms]
  `(defn ~(symbol (str "run-" name))
     []
     (let [r# (do ~@forms)]
       (shutdown-agents)
       r#)))

(defrun convert-redis-keys-from-staging-to-dev!
  (convert-redis-keys-from-staging-to-dev!))

(defrun build-watcher-indexes!
  (doall (build-watcher-indexes!)))

(defrun check-interest-coherence
  (doall (check-interest-coherence)))

(defrun check-watcher-coherence
  (doall (check-watcher-coherence)))

(comment
  (convert-redis-keys-from-staging-to-dev!)
  (build-watcher-indexes)
  (check-interest-coherence)
  (check-watcher-coherence)
  (redis/with-connection (redis/connection-map {}) (queries/watchers-keys))
  (redis/with-connection (redis/connection-map {}) (queries/interest-keys))

  (redis/with-connection (redis/connection-map {}) (queries/keys))

  (redis/with-connection (redis/connection-map {}) (queries/keys))
  (redis/with-connection (redis/connection-map {}) (redis/keys "*")))