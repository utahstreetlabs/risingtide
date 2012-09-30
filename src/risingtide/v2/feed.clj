(ns risingtide.v2.feed)

(defprotocol Feed
  (add [feed story] ""))

(deftype DigestFeed [stories]
  Feed
  (add [this story] (->DigestFeed (conj stories story)))
  clojure.lang.Seqable
  (seq [this] stories))

(defn new-digest-feed
  []
  (->DigestFeed nil))