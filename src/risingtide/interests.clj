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
  (:require [risingtide.redis :as redis]
            [risingtide.key :as key]
            [clojure.tools.logging :as log]))

(defn interest-token
  [type object-id]
  (str type ":" object-id))

(defn- add-interest!
  "Generate redis commands for registering a user's interest in an object of a given type."
  [redii interested-user-id type object-id]
  (redis/with-jedis* (:watchers redii)
    (fn [jedis] (.sadd jedis (key/watchers type object-id) (str interested-user-id))))
  (redis/with-jedis* (:interests redii)
    (fn [jedis] (.sadd jedis (key/interest interested-user-id type) (interest-token type object-id)))))

(defn- remove-interest!
  "Generate redis commands for deregistering a user's interest in an object of a given type."
  [redii interested-user-id type object-id]
  (redis/with-jedis* (:watchers redii)
    (fn [jedis] (.srem jedis (key/watchers type object-id) (str interested-user-id))))
  (redis/with-jedis* (:interests redii)
    (fn [jedis] (.srem jedis (key/interest interested-user-id type) (interest-token type object-id)))))

(defn add!
  "Given a redis connection map, a user id, a type and an object, connects to redis and registers
the user's interest in the specified object."
  [redii interested-user-id type object-id]
  (add-interest! redii interested-user-id (first-char type) object-id))

(defn remove!
  "Given a redis connection map, a user id, a type and an object, connects to redis and registers
the user's interest in the specified object."
  [redii interested-user-id type object-id]
  (remove-interest! redii interested-user-id (first-char type) object-id))

(def interests-for-feed-type
  {:card (map first-char [:actor :listing :tag])
   :network (map first-char [:actor])})

(defn- feed-source-interest-keys
  "given a feed type and a user id, get the keys of sets that will serve
as sources for that feed

currently, returns the actor interest key for network feeds and actor, listing and tag
interest keys for card feeds"
  [feed-type user-id]
  (map #(key/interest user-id %)
       (interests-for-feed-type feed-type)))

(defn feed-stories
  [redii user-id feed-type]
  (redis/with-jedis* (:interests redii)
    (fn [jedis] (.sunion jedis (into-array String (feed-source-interest-keys feed-type user-id))))))

(defn watchers
  [redii keys]
  (redis/with-jedis* (:watchers redii)
    (fn [jedis] (.sunion jedis (into-array String keys)))))
