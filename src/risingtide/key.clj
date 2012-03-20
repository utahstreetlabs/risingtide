(ns risingtide.key
  "encapsulate key naming conventions for risingtide"
  (:use [risingtide.core :only [env first-char]])
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

;; watchers - lists of interested users by object:id keys

(defn watchers
  [type id]
  (format-key type id))

(defn actor-watchers
  [actor-id]
  (watchers "a" actor-id))

(defn listing-watchers
  [listing-id]
  (watchers "l" listing-id))

(defn tag-watchers
  [tag-id]
  (watchers "t" tag-id))

;; feeds

(defn feed
  [& parts]
  (apply format-key "f" parts))

(defn user-feed
  [id type]
  (feed "u" id (first-char type)))

(defn everything-feed
  []
  (feed "c"))

(def feed-type {"c" :card "n" :network})

(defn feed-type-user-id-from-key
  [key]
  (let [parts (.split key ":")] [(feed-type (aget parts 4)) (aget parts 3)]))