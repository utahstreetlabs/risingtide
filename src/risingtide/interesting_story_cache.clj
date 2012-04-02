(ns risingtide.interesting-story-cache
  (:use risingtide.core)
  (:require [clojure.set :as set]
            [accession.core :as redis]
            [risingtide
             [stories :as stories]
             [key :as key]]))

;;;; Das Cache ;;;;

(defn- empty-cache
  ;; take arbitrary args to work with swap
  [& args]
  {})

(def interesting-story-cache (atom (empty-cache)))

(defn reset-cache! [] (swap! interesting-story-cache empty-cache))

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

(defn interesting-key-query
  [feed-type user-id]
  (apply redis/sunion (feed-source-interest-keys feed-type user-id)))

(defn get-interesting-stories-for-feed
  ([cache feed-type user-id]
     (get-in cache [user-id feed-type]))
  ([cache feed-key] (apply get-interesting-stories-for-feed cache (key/type-user-id-from-feed-key feed-key))))

(defn cache-interesting-stories-for-feed!
  ([cache-atom stories feed-type user-id]
     (swap! cache-atom #(assoc-in % [user-id feed-type] (into #{} stories))))
  ([cache-atom stories feed-key]
     (apply cache-interesting-stories-for-feed! cache-atom stories (key/type-user-id-from-feed-key feed-key))))


(defn feeds-to-update
  "for an interest type like :actor or :tag or :listing and a user id, return a list of
feeds that will need to be updated"
  [type user-id]
  (cons (key/user-feed user-id "c")
        (when (= type :actor) [(key/user-feed user-id "n")])))

(defn feed-interest
  [conn cache user-id feed-type]
  (or
   (get-interesting-stories-for-feed @interesting-story-cache feed-type user-id)
   (let [stories (redis/with-connection conn (interesting-key-query feed-type user-id))]
     (cache-interesting-stories-for-feed! interesting-story-cache stories feed-type user-id)
     stories)))

(defn update-feed-interest!
  [redii cache-atom interest-token feed-keys operation]
  (swap! cache-atom
         #(reduce (fn [cache feed-key]
                    (let [[feed-type user-id] (key/type-user-id-from-feed-key feed-key)]
                      (assoc-in cache [user-id feed-type]
                                (conj (feed-interest redii @cache-atom user-id feed-type) interest-token))))
                  % feed-keys)))

(defn add-interest-to-feeds!
  [redii cache-atom interest-token feed-keys]
  (update-feed-interest! redii cache-atom interest-token feed-keys conj))

(defn remove-interest-from-feeds!
  [redii cache-atom interest-token feed-keys]
  (update-feed-interest! redii cache-atom interest-token feed-keys disj))
