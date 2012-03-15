(ns risingtide.utils
  (:require [accession.core :as redis]))

(defn convert-redis-keys-from-staging-to-dev!
  [con]
  (apply redis-with-connection con
         (for [key (redis/with-connection con (redis/keys))]
           (redis/rename key (.replaceFirst key "mags" "magd")))))
