(ns risingtide.persist
  "Utilities for persisting data"
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

;;; decoding stories from redis

(defn convert [story value-converter & keys]
  (reduce (fn [story key]
            (if (get story key)
                           (assoc story key (value-converter (get story key)))
                           story))
          story keys))

(defn convert-to-set-with-converter [story value-converter & keys]
  (apply convert story #(set (map value-converter %)) keys))

(defn convert-to-kw-set [story & keys]
  (apply convert-to-set-with-converter story keyword keys))

(defn convert-to-set [story & keys]
  (apply convert-to-set-with-converter story identity keys))

(defn convert-to-seq-with-converter [story value-converter & keys]
  (apply convert story #(map value-converter %) keys))

(defn convert-to-kw-seq [story & keys]
  (apply convert-to-seq-with-converter story keyword keys))

(defn keywordize [story & keys]
  (apply convert story keyword keys))
