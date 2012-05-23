(ns risingtide.riak
  "Generic riak utilities"
  (:use risingtide.core)
  (:require [risingtide.config :as config]
            [sumo.client :as sumo]
            [clojure.pprint :as pp]
            [clojure.walk :as walk]))

(defn client
  ([] (sumo/connect-pb))
  ([host port] (sumo/connect-pb host port)))

(defn riaks
  [env]
  (walk/walk (fn [[k v]] [k (apply client v)]) identity (config/riak env)))



