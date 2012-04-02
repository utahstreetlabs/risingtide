(ns risingtide.interests
  "Utilities for managing interests.

Interests are currently stored in two separate indexes in Redis:

I. Interest index

The interest index maps keys like

magX:i:u:<user id>:<object type>

to sets of \"interest tokens\" representing objects of the given type that the user
has shown interest in, like:

#{\"t:7\" \"t:8\" \"t:11\"}

Object types are included in the interest tokens so that they can be unioned inside redis
and maintain type information.

II. Watcher index

The watcher index maps keys like

magX:w:<object type>:<object id>

to sets of user ids like:

#{47, 34, 15}
"
  (:use risingtide.core)
  (:require [accession.core :as redis]
            [risingtide.key :as key]
            [clojure.tools.logging :as log]))

(defn interest-token
  [type object-id]
  (str type ":" object-id))

(defn- add-interest
  "Generate redis commands for registering a user's interest in an object of a given type."
  [interested-user-id type object-id]
  [(redis/multi)
   (redis/sadd (key/watchers type object-id) interested-user-id)
   (redis/sadd (key/interest interested-user-id type) (interest-token type object-id))
   (redis/exec)])

(defn- remove-interest
  "Generate redis commands for deregistering a user's interest in an object of a given type."
  [interested-user-id type object-id]
  [(redis/multi)
   (redis/srem (key/watchers type object-id) interested-user-id)
   (redis/srem (key/interest interested-user-id type) (interest-token type object-id))
   (redis/exec)])

(defn add!
  "Given a redis connection map, a user id, a type and an object, connects to redis and registers
the user's interest in the specified object."
  [redii interested-user-id type object-id]
  (apply redis/with-connection (:interests redii) (add-interest interested-user-id (first-char type) object-id)))

(defn remove!
  "Given a redis connection map, a user id, a type and an object, connects to redis and registers
the user's interest in the specified object."
  [redii interested-user-id type object-id]
  (apply redis/with-connection (:interests redii) (remove-interest interested-user-id (first-char type) object-id)))




