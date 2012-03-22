(ns risingtide.interests
  "utilities for managing interests"
  (:use risingtide.core)
  (:require [accession.core :as redis]
            [risingtide.key :as key]
            [clojure.tools.logging :as log]))

(defn interest-token
  [type object-id]
  (str type ":" object-id))

(defn add-interest
  [interested-id type object-id]
  [(redis/multi)
   (redis/sadd (key/watchers type object-id) interested-id)
   (redis/sadd (key/interest interested-id type) (interest-token type object-id))
   (redis/exec)])

(defn remove-interest
  [interested-id type object-id]
  [(redis/multi)
   (redis/srem (key/watchers type object-id) interested-id)
   (redis/srem (key/interest interested-id type) (interest-token type object-id))
   (redis/exec)])

(defmacro definterest-helpers
  [name]
  (let [type (first (str name))]
   `(do
      (defn ~(symbol (str "add-interest-in-" name))
        [interested-id# object-id#]
        (add-interest interested-id# ~type object-id#))
      (defn ~(symbol (str "remove-interest-in-" name))
        [interested-id# object-id#]
        (remove-interest interested-id# ~type object-id#)))))

;; generate add-interest-in-{actor,listing,tag} functions
(definterest-helpers actor)
(definterest-helpers listing)
(definterest-helpers tag)

(comment
 (redis/with-connection (redis/connection-map {})
   (remove-interest-in-listing 123 789))

 (redis/with-connection (redis/connection-map {})
   (redis/smembers (key/interest 47 "a")))

 (redis/with-connection (redis/connection-map {})
   (risingtide.queries/card-story-keys)))



