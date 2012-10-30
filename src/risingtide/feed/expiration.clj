(ns risingtide.feed.expiration
  (:require [risingtide
             [core :refer [now]]
             [config :refer [*digest-cache-ttl*]]]
            [risingtide.model
             [timestamps :refer [max-timestamp]]]))

(defn expiration-threshold
  []
  (- (now) *digest-cache-ttl*))

(defn expired?
  [story]
  (<= (max-timestamp story) (expiration-threshold)))

(defn expire [feed]
  (filter #(not (expired? %)) (seq feed)))
