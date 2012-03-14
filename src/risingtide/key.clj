(ns risingtide.key
  "encapsulate key naming conventions for risingtide"
  (:use [risingtide.core :only [env]])
  (:require [clojure.string :as s])
  (:refer-clojure :exclude [format] ))

(defn prefix
  []
  (str "mag" (first (name (env)))))

(defn format
  [& parts]
  (s/join  ":" (map str (cons (prefix) parts))))

(defn interest
  [user-id type]
  (format "i:u" user-id type))

(defn card-story
  [& parts]
  (apply format "c" parts))

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
  (apply format "n" parts))

(defn feed
  [& parts]
  (apply format "f" parts))

(defn user-feed
  [id type]
  (feed "u" id type))

(defn everything-feed
  []
  (feed "c"))
