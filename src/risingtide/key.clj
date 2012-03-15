(ns risingtide.key
  "encapsulate key naming conventions for risingtide"
  (:use [risingtide.core :only [env]])
  (:require [clojure.string :as s]))

(defn env-prefix
  []
  (str "mag" (first (name (env)))))

(defn format-key
  [& parts]
  (s/join  ":" (map str (cons (env-prefix) parts))))

(defn interest
  [user-id type]
  (format-key "i:u" user-id type))

(defn card-story
  [& parts]
  (apply format-key "c" parts))

(defn card-actor-story
  [actor-id]
  (card-story "a" actor-id))

(defn card-listing-story
  [listing-id]
  (card-story "l" listing-id))

(defn card-tag-story
  [tag-id]
  (card-story "t" tag-id))

(defn network-story
  [& parts]
  (apply format-key "n" parts))

(defn network-actor-story
  [actor-id]
  (network-story "a" actor-id))

(defn network-user-story
  [actor-id]
  (network-story "u" actor-id))

(defn network-listing-story
  [listing-id]
  (network-story "l" listing-id))

(defn network-tag-story
  [tag-id]
  (network-story "t" tag-id))

(defn feed
  [& parts]
  (apply format-key "f" parts))

(defn user-feed
  [id type]
  (feed "u" id type))

(defn everything-feed
  []
  (feed "c"))
