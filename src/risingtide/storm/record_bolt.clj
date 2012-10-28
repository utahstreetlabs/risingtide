(ns risingtide.storm.record-bolt
  (:require
   [risingtide.core :refer [now]]
   [risingtide.model
    [story :as story]
    [timestamps :refer [with-timestamp]]]
   [backtype.storm [clojure :refer [ack! defbolt emit-bolt!]]]
   [clojure.string :as str]))

(defn dash-case-keys [story]
  (into {} (map (fn [[k v]] [(keyword (str/replace (name k) "_" "-")) v]) story)))

(defn story-to-record [story]
  (with-timestamp
   ((story/story-factory-for (keyword (:type story)))
    (-> story
        dash-case-keys
        (dissoc :type :timestamp)
        (assoc :feed (map keyword (:feed story)))))
   (or (:timestamp story) (now))))

(defbolt record-bolt ["id" "story"] [{id "id" story "story" :as tuple} collector]
  (emit-bolt! collector [id (story-to-record story)])
  (ack! collector tuple))

