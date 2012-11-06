(ns risingtide.storm.story-bolts
  (:require
   [risingtide.core :refer [now]]
   [risingtide.model
    [story :as story]
    [timestamps :refer [with-timestamp]]]
   [backtype.storm [clojure :refer [ack! defbolt emit-bolt!]]]
   [clojure.string :as str]))

(defn dash-case-keys [story]
  (into {} (map (fn [[k v]] [(keyword (str/replace (name k) "_" "-")) v]) story)))

(defn action-to-story [action]
  (with-timestamp
   ((story/story-factory-for (keyword (:type action)))
    (-> action
        dash-case-keys
        (dissoc :type :timestamp)))
   (or (:timestamp action) (now))))

(defbolt create-story-bolt ["id" "user-ids" "story"] [{id "id" user-ids "user-ids" action "action" :as tuple} collector]
  (emit-bolt! collector [id user-ids (action-to-story action)])
  (ack! collector tuple))

