(ns risingtide.interesting-story-cache
  (:use risingtide.core)
  (:require [clojure.set :as set]
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

(defn update-feed-interest!
  [cache-atom interest-token feed-keys operation]
  (swap! cache-atom
         #(reduce (fn [cache feed-key]
                    (let [[feed-type user-id] (key/type-user-id-from-feed-key feed-key)]
                      (assoc-in cache [user-id feed-type]
                                (conj (get-in cache [user-id feed-type]) interest-token))))
                  % feed-keys)))

(defn add-interest-to-feeds!
  [cache-atom interest-token feed-keys]
  (update-feed-interest! cache-atom interest-token feed-keys conj))

(defn remove-interest-from-feeds!
  [cache-atom interest-token feed-keys]
  (update-feed-interest! cache-atom interest-token feed-keys disj))

(comment
  (cache-interesting-stories-for-feed! interesting-story-cache  [1 2 3] "magt:f:u:1:c")
  (get-interesting-stories-for-feed @interesting-story-cache "magt:f:u:1:c")
  )











