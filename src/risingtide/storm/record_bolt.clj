(ns risingtide.storm.record-bolt
  (:require
   [risingtide.core :refer [now]]
   [risingtide.model.story :as story]
   [backtype.storm [clojure :refer [ack! defbolt emit-bolt!]]]
   [clojure.string :as str]))

(defn dash-case-keys [story]
  (into {} (map (fn [[k v]] [(keyword (str/replace (name k) "_" "-")) v]) story)))

(defn story-to-record [story]
  (story/with-score
   ((story/story-factory-for (keyword (:type story)))
    (-> story
        dash-case-keys
        (dissoc :type)
        (assoc :feed (map keyword (:feed story)))))
   (or (:timestamp story) (now))))

(defbolt record-bolt ["story"] [tuple collector]
  (let [{story "story"} tuple
        record (story-to-record story)]
    (emit-bolt! collector [record])
    (ack! collector tuple)))

