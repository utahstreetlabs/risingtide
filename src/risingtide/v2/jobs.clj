(ns risingtide.v2.jobs
  (:require [clojure.string :as str]
            [risingtide.core :refer [now]]
            [risingtide.v2.story :as s]
            [clojure.set :refer [rename-keys]]))

(defn dash-case-keys [story]
  (into {} (map (fn [[k v]] [(keyword (str/replace (name k) "_" "-")) v]) story)))

(defn story-to-record [story score]
  ((s/story-factory-for (:type story)) (-> (assoc story :score score)
                                           dash-case-keys
                                           rename-keys {:type :action})))

