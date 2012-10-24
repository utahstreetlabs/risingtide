(ns risingtide.utils
  (:use risingtide.core)
  (:require [clojure.data.json :as json]
            [risingtide
             [config :as config]
             [key :as key]
             [stories :as stories]
             [redis :as redis]]))

(defn- env-connection-config
  []
  (redis/redii config/env))

;; migrate staging keys to development.

(defn convert-redis-keys-from-staging-to-dev!
  ([redii]
     (doseq [[k pool] redii]
      (if (= :development config/env)
        (redis/with-jedis* pool
          (fn [jedis]
            (for [key (.keys jedis "*")]
              (.rename jedis key (.replaceFirst key "mags" "magd")))))
        (prn "holy shit. you really, really don't want to rename keys in" config/env))))
  ([] (convert-redis-keys-from-staging-to-dev! (env-connection-config))))


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
