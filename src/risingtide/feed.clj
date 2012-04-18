(ns risingtide.feed
  "utilities for building feeds"
  (:use risingtide.core)
  (:require [clojure.tools.logging :as log]
            [accession.core :as redis]
            [risingtide.config :as config]
            [risingtide.key :as key]
            [risingtide.stories :as stories]
            [risingtide.queries :as queries]
            [risingtide.interests :as interesting]))

;;;; truncation ;;;;

(def ^:dynamic *max-card-feed-size* config/max-card-feed-size)
(def ^:dynamic *max-network-feed-size* config/max-network-feed-size)

(defn- truncation-range
  [feed]
  (case (last feed)
    \c [0 (- 0 *max-card-feed-size* 1)]
    \n [0 (- 0 *max-network-feed-size* 1)]))

(defn truncate
  [feed]
  (apply redis/zremrangebyrank feed (truncation-range feed)))

;;;; keys ;;;;

(defn feed-type-key [feed-type]
  "given a feed type keyword return the single character name to use when constructing keys"
  (first-char feed-type))

(defn interesting-story-keys
  "return the story keys of sets that should be included in the a user's feed of the given type"
  [redii feed-type user-id]
  (let [f (feed-type-key feed-type)]
    (map #(key/format-key f %) (interesting/feed-stories redii user-id feed-type))))


;;;; filtering ;;;;

(def ev-feed-token "ev")
(def ev-feed-token? #{ev-feed-token})

(def user-feed-token "ylf")
(def user-feed-token? #{user-feed-token})

(def default-feeds [ev-feed-token user-feed-token])

(defn for-feed-with-token?
  [story token token-set]
  (let [f (get story :feed default-feeds)]
    (or (= f nil) (= f token) (some token-set f))))

(defn for-everything-feed?
  [story]
  (for-feed-with-token? story ev-feed-token ev-feed-token?))

(defn for-user-feed?
  [story]
  (for-feed-with-token? story user-feed-token user-feed-token?))

(defn everything-feed-stories
  [stories]
  (filter for-everything-feed? stories))

(defn user-feed-stories
  [stories]
  (filter for-user-feed? stories))


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
  [redii feeds f]
  (map #(apply f %)
       (map (fn [[redis-key feeds]] [(redii redis-key) feeds]) (feeds-by-type feeds))))

(defmacro with-connections-for-feeds
  [redii feeds params & body]
  `(doall (map-across-connections-and-feeds ~redii ~feeds (fn ~params ~@body))))

(defmacro with-connection-for-feed
  [redii feed-key connection-vec & body]
  `(first (feed/with-connections-for-feeds ~redii [~feed-key] [~(first connection-vec) _#] ~@body)))

;;;; redigesting ;;;;

(defn- scored-encoded-stories
  [stories]
  (interleave (map :score stories) (map stories/encode stories)))

(defn replace-feed-head-query
  [feed stories low-score high-score]
  (if (empty? stories)
    []
    [(redis/zremrangebyscore feed low-score high-score)
     (apply redis/zadd feed (scored-encoded-stories stories))
     (truncate feed)]))

(defn add!
  "add a story with the given score to the set of feeds that are interested in it"
  [redii story score]
  (let [encoded-story (stories/encode story)]
    (with-connections-for-feeds redii (stories/interested-feeds redii story) [connection feeds]
      (apply redis/with-connection connection
             (interleave
              (map #(redis/zadd % score encoded-story) feeds)
              (map truncate feeds))))))