(ns risingtide.feed
  "utilities for building feeds"
  (:use risingtide.core)
  (:require [clojure.tools.logging :as log]
            [risingtide.key :as key]
            [risingtide.interests :as interesting]))

;;;; keys ;;;;

(defn feed-type-key [feed-type]
  "given a feed type keyword return the single character name to use when constructing keys"
  (first-char feed-type))

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

;; these functions use persisted data

(defn interesting-story-keys
  "return the story keys of sets that should be included in the a user's feed of the given type"
  [feed-type user-id]
  (let [f (feed-type-key feed-type)]
    (map #(key/format-key f %) (interesting/feed-stories user-id))))

