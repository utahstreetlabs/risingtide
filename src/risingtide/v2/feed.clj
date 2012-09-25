(ns risingtide.v2.feed)

(defprotocol Feed
  (add [feed story] ""))

(deftype DigestFeed [stories]
  Feed
  (add [this story] (conj stories story))
  clojure.lang.Seqable
  (seq [this] stories))
