(ns risingtide.key
  "encapsulate key naming conventions for risingtide"
  (:require [clojure.string :as s])
  (:refer-clojure :exclude [format] ))

(defn env [] (keyword (or (System/getenv "RISINGTIDE_ENV") "development")))

(defn prefix
  []
  (str "mag" (first (name (env)))))

(defn format
  [& parts]
  (s/join  ":" (map str (cons (prefix) parts))))

(defn interest
  [& parts]
  (apply format "i:u" parts))

(defn feed
  [& parts]
  (apply format "f" parts))

(defn card-story
  [& parts]
  (apply format "c" parts))

(defn network-story
  [& parts]
  (apply format "n" parts))

(defn everything-feed
  []
  (key/feed "c"))
