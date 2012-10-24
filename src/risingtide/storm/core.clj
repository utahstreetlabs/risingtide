(ns risingtide.storm.core
  (:require [risingtide.v2
             [feed :refer [add]]]
            [risingtide.v2.feed
             [digest :refer [new-digest-feed]]
             [filters :refer [for-everything-feed? for-user-feed?]]]
            [risingtide.storm.story-spout :refer [resque-spout record-bolt]]
            [risingtide.interests
             [brooklyn :as follows]
             [pyramid :as likes]]
            [risingtide.config :as config]
            [backtype.storm [clojure :refer :all] [config :refer :all]])
  (:import [backtype.storm StormSubmitter LocalCluster]))

(defn active-users []
  [47])

(defbolt active-user-bolt ["user-id" "story"] [tuple collector]
  (let [{story "story"} tuple]
    (when (for-user-feed? story)
     (doseq [user-id (active-users)]
       (emit-bolt! collector [user-id story]))))
  (ack! collector tuple))


(defn like-score [user-id story]
  (if (likes/likes? user-id (:listing-id story)) 1 0))

(defbolt like-interest-scorer ["user-id" "story" "score" "type"]  [tuple collector]
  (let [{user-id "user-id" story "story"} tuple]
    (emit-bolt! collector [user-id story (like-score user-id story) :like]))
  (ack! collector tuple))


(defn follow-score [user-id story]
  (if (follows/following? user-id (:actor-id story)) 1 0))

(defbolt follow-interest-scorer ["user-id" "story" "score" "type"]  [tuple collector]
  (let [{user-id "user-id" story "story"} tuple]
    (emit-bolt! collector [user-id story (follow-score user-id story) :follow]))
  (ack! collector tuple))


(defbolt interest-reducer ["user-id" "story" "score"] {:prepare true}
  [conf context collector]
  (let [scores (atom {})]
    (bolt
     (execute [tuple]
              (let [{user-id "user-id" story "story" type "type" score "score"} tuple]
                (swap! scores #(assoc-in % [[user-id story] type] score))
                (let [story-scores (get @scores [user-id story])
                      scored-types (set (keys story-scores))]
                  (when (= scored-types #{:follow :like})
                    (let [total-score (apply + (vals story-scores))]
                      (when (>= total-score 1) (emit-bolt! collector [user-id story total-score])))
                    (swap! scores #(dissoc % [user-id story])))))
              (ack! collector tuple)))))

(defbolt add-to-feed [] {:prepare true}
  [conf context collector]
  (let [feed-set (atom {})]
    (bolt
     (execute [tuple]
              (let [{user-id "user-id" story "story" score "score"} tuple]
                (swap! feed-set #(update-in % [user-id] (fn [v] (add (or v (new-digest-feed)) story))))
                (emit-bolt! collector [(seq (@feed-set user-id))])
                (ack! collector tuple))))))

(defbolt add-to-curated-feed [] {:prepare true}
  [conf context collector]
  (let [feed (atom (new-digest-feed))]
    (bolt
     (execute [tuple]
              (let [{story "story"} tuple]
                (when (for-everything-feed? story)
                  (swap! feed #(add % story))
                  (emit-bolt! collector [(seq @feed)]))
                (ack! collector tuple))))))

(defn feed-generation-topology []
  (topology
   {"stories" (spout-spec resque-spout)}

   ;; everything feed

   {"records" (bolt-spec {"stories" :shuffle}
                         record-bolt)

    "curated-feed" (bolt-spec {"records" :global}
                              add-to-curated-feed
                              :p 1)

    ;; user feeds

    "active-users" (bolt-spec {"records" :shuffle}
                              active-user-bolt
                              :p 1)

    "likes" (bolt-spec {"active-users" :shuffle}
                       like-interest-scorer
                       :p 2)
    "follows" (bolt-spec {"active-users" :shuffle}
                         follow-interest-scorer
                         :p 2)

    "interest-reducer" (bolt-spec {"likes" ["user-id" "story"]
                                   "follows" ["user-id" "story"]}
                                  interest-reducer
                                  :p 5)

    "add-to-feed" (bolt-spec {"interest-reducer" ["user-id"]}
                             add-to-feed
                             :p 20)
  }))

(defn run-local! []
  (let [cluster (LocalCluster.)]
    (.submitTopology cluster "story" {TOPOLOGY-DEBUG true} (feed-generation-topology))))


(comment
  ;; lein run -m risingtide.storm.core/run-local!
  ;; brooklyn:
  ;; User.inject_listing_story(:listing_liked, 2, Listing.find(23))
  )