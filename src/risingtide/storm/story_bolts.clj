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

(defn action-to-story [story]
  (with-timestamp
   ((story/story-factory-for (keyword (:type story)))
    (-> story
        dash-case-keys
        (dissoc :type :timestamp)))
   (or (:timestamp story) (now))))

(defbolt create-story-bolt ["id" "user-id" "story"] [{id "id" user-id "user-id" action "action" :as tuple} collector]
  (emit-bolt! collector [id user-id (action-to-story action)])
  (ack! collector tuple))

