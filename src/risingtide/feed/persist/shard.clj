(ns risingtide.feed.persist.shard
  "Utilities for sharding

Currently supports storing different feeds on different redii.

Shards are configured as different redis configs in risingtide.config/redis.
"
  (:require [clojure.string :as str]
            [risingtide [key :as key]]
            [risingtide.feed.persist.shard [config :as shard-config]]))

;;;; connection negotation ;;;;
;;
;; not all feeds live on the same redis. these utilities make it
;; easier to live in this world.

(def shard-type-keys {:card :card-feeds})

(defn shard-config-key
  [type shard-key]
  (keyword (str/join "-" [(name (shard-type-keys type)) shard-key])))

(defn- flatten-keys
  "Flatten a nested map into a flat keywordized map

Thanks, Jay Fields:

http://blog.jayfields.com/2010/09/clojure-flatten-keys.html"
  ([a ks m key-separator]
     (if (map? m)
       (reduce into (map (fn [[k v]] (flatten-keys a (conj ks k) v key-separator)) (seq m)))
       (assoc a (keyword (str/join key-separator (map name ks))) m)))
  ([m key-separator]
     (flatten-keys {} [] m key-separator))
  ([m] (flatten-keys m "")))

(defn- add-feed-to-card-shards
  "find the shards the given feed should be stored on "
  [shard-feed-aggregator conn-spec user-id feed]
  (reduce (fn [m shard-key] (update-in m [(shard-type-keys :card) shard-key] #(cons feed %)))
          shard-feed-aggregator (shard-config/card-feed-shard-keys conn-spec user-id)))

(defn- feeds-by-shard
  "Return a map from keys matching redis config keys in risingtide.config/redis
to feeds that should be stored on the corresponding redis instance.
"
  [conn-spec feeds]
  (-> (reduce
       (fn [m feed]
         (if (= feed (key/everything-feed))
           (assoc-in m [:everything-card-feed] [feed])
           (let [[type user-id] (key/type-user-id-from-feed-key feed)]
             (case type
               :card (add-feed-to-card-shards m conn-spec user-id feed)
               :network (update-in m [(shard-type-keys :network)] #(cons feed %))))))
       {} feeds)
      (flatten-keys "-")))

(defn map-across-connections-and-feeds
  "given a connection-spec map, a collection of feeds and function of two arguments like

 (fn [connection feeds] (do-stuff))

call the function once for each set of feeds that live on different servers. The function
will be passed the connection spec for the server to use and the set of feeds that live
on the server specified by that connection spec.
"
  [conn-spec feeds f]
  (let [fbs (feeds-by-shard conn-spec feeds)]
    (map #(apply f %)
         (map (fn [[conn-key feeds]] [(conn-spec conn-key) feeds])
              fbs))))

(defmacro with-connections-for-feeds
  [conn-spec feeds params & body]
  `(doall (map-across-connections-and-feeds ~conn-spec ~feeds (fn ~params ~@body))))

(defmacro with-connection-for-feed
  [conn-spec feed-key connection-vec & body]
  `(first (with-connections-for-feeds ~conn-spec [~feed-key] [~(first connection-vec) _#] ~@body)))

(defn add-migration!
  [feed-key destination-shard]
  (let [[type user-id] (key/type-user-id-from-feed-key feed-key)]
    (shard-config/add-migration! type user-id destination-shard)))

(defn remove-migration!
  [feed-key]
  (let [[type user-id] (key/type-user-id-from-feed-key feed-key)]
    (shard-config/remove-migration! type user-id)))

(defn shard-key
  [conn-spec feed-key]
  (let [[type user-id] (key/type-user-id-from-feed-key feed-key)]
    (shard-config/get-shard-key conn-spec type user-id)))

(defn shard-conn
  ([conn-spec feed-key shard-key]
     (let [[type _] (key/type-user-id-from-feed-key feed-key)]
       (conn-spec (shard-config-key type shard-key))))
  ([conn-spec feed-key] (shard-conn conn-spec feed-key (shard-key conn-spec feed-key))))

(defn update-shard-config!
  [conn-spec feed-key destination-shard]
  (let [[type user-id] (key/type-user-id-from-feed-key feed-key)]
    (shard-config/update-shard-key conn-spec type user-id destination-shard)))
