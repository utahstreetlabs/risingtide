(ns risingtide.key
  "encapsulate key naming conventions for risingtide"
  (:require [clojure.string :as s]
            [risingtide.config :refer [env]]))

(defn env-prefix
  []
  (str "mag" (first (name env))))

(defn format-key
  [& parts]
  (s/join  ":" (map str (cons (env-prefix) parts))))

;; feeds

(defn feed
  [& parts]
  (apply format-key "f" parts))

(defn user-feed
  [id]
  (feed "u" id "c"))

(defn everything-feed
  []
  (feed "c"))

(def feed-type {"c" :card "n" :network})

(defn type-user-id-from-feed-key
  [key]
  (let [parts (.split key ":")] [(feed-type (aget parts 4)) (aget parts 3)]))

;; stories

(defn card-story
  [& parts]
  (apply format-key "c" parts))

(defn card-listing-story
  [listing-id]
  (card-story "l" listing-id))
