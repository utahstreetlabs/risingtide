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

(defn actor-card-story
  [actor-id]
  (card-story "a" actor-id))

(defn listing-card-story
  [listing-id]
  (card-story "l" listing-id))

(defn tag-card-story
  [tag-id]
  (card-story "t" tag-id))

(defn network-story
  [& parts]
  (apply format-key "n" parts))

(defn actor-network-story
  [actor-id]
  (network-story "a" actor-id))

(defn listing-network-story
  [listing-id]
  (network-story "l" listing-id))

(defn tag-network-story
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
