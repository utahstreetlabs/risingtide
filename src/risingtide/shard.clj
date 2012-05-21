(ns risingtide.shard
 "Utilities for sharding")

;;;; connection negotation ;;;;
;;
;; not all feeds live on the same redis. these utilities make it
;; easier to live in this world.

(defn feeds-by-type
  [feeds]
  (reduce
   (fn [m feed]
     (case (last feed)
       \c (assoc m :card-feeds (cons feed (get m :card-feeds)))
       \n (assoc m :network-feeds (cons feed (get m :network-feeds)))))
   {} feeds))

(defn map-across-connections-and-feeds
  "given a redii map, a collection of feeds and function of two arguments like

 (fn [connection feeds] (do-stuff))

call the function once for each set of feeds that live on different servers. The function
will be passed the connection spec for the server to use and the set of feeds that live
on the server specified by that connection spec.
"
  [conn-spec feeds f]
  (map #(apply f %)
       (map (fn [[conn-key feeds]] [(conn-spec conn-key) feeds]) (feeds-by-type feeds))))

(defmacro with-connections-for-feeds
  [conn-spec feeds params & body]
  `(doall (map-across-connections-and-feeds ~conn-spec ~feeds (fn ~params ~@body))))

(defmacro with-connection-for-feed
  [conn-spec feed-key connection-vec & body]
  `(first (with-connections-for-feeds ~conn-spec [~feed-key] [~(first connection-vec) _#] ~@body)))


