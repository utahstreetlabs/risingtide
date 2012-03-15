(ns risingtide.feed
  "utilities for building feeds"
  (:use risingtide.core)
  (:require [clojure.tools.logging :as log]
            [accession.core :as redis]
            [risingtide.key :as key]))

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

(defn feed-type-key [feed-type]
  "given a feed type keyword return the single character name to use when constructing keys"
  (first-char feed-type))

(defn interesting-keys
  "return the keys of sets that should be included in the a user's feed of the given type"
  [conn feed-type user-id]
  (let [f (feed-type-key feed-type)]
    (map #(key/format-key f %)
         (redis/with-connection conn
           (apply redis/sunion (feed-source-interest-keys feed-type user-id))))))

(defn build-feed
  "returns a query that will build and store a feed of the given type for a user"
  [feed-type user-id interest-keys]
  (let [feed-key (key/user-feed user-id (feed-type-key feed-type))]
    (log/info "Generating feed" feed-key)
    (zunionstore feed-key interest-keys "AGGREGATE" "MIN")))
