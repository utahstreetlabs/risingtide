(ns risingtide.shard
  "Utilities for sharding

Currently supports storing different feeds on different redii.

Shards are configured as different redis configs in risingtide.config/redis.
"
  (:require [clojure.string :as str]
            [risingtide [key :as key]]
            [risingtide.shard [config :as shard-config]]))

;;;; connection negotation ;;;;
;;
;; not all feeds live on the same redis. these utilities make it
;; easier to live in this world.


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

(defn- feeds-by-shard
  "Return a map from keys matching redis config keys in risingtide.config/redis
to feeds that should be stored on the corresponding redis instance.
"
  [shard-config-conn feeds]
  (-> (reduce
       (fn [m feed]
         (if (= feed (key/everything-feed))
           (assoc-in m [:everything-card-feed] [feed])
           (let [[type user-id] (key/type-user-id-from-feed-key feed)]
             (case type
               :card (update-in m [:card-feeds (shard-config/card-feed-shard-key shard-config-conn user-id)]
                                #(cons feed %))
               :network (update-in m [:network-feeds] #(cons feed %))))))
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
  (map #(apply f %)
       (map (fn [[conn-key feeds]] [(conn-spec conn-key) feeds]) (feeds-by-shard (:shard-config conn-spec) feeds))))

(defmacro with-connections-for-feeds
  [conn-spec feeds params & body]
  `(doall (map-across-connections-and-feeds ~conn-spec ~feeds (fn ~params ~@body))))

(defmacro with-connection-for-feed
  [conn-spec feed-key connection-vec & body]
  `(first (with-connections-for-feeds ~conn-spec [~feed-key] [~(first connection-vec) _#] ~@body)))


