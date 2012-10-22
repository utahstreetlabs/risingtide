(ns risingtide.storm.core
  (:require [clojure.data.json :as json]
            [risingtide.v2.story :refer [->ListingLikedStory ->ListingCommentedStory ->ListingActivatedStory]]
            [risingtide.v2.watchers :refer [watchers]]
            [risingtide.v2.feed :refer [add]]
            [risingtide.v2.feed.digest :refer [new-digest-feed]]
            [backtype.storm [clojure :refer :all] [config :refer :all]])
  (:import [redis.clients.jedis Jedis JedisPool JedisPoolConfig]
           [backtype.storm StormSubmitter LocalCluster]
           [risingtide.v2.story ListingLikedStory]))

(def pool (JedisPool. (JedisPoolConfig.) "localhost"))

(defspout resque-spout ["resque"]
  [conf context collector]
  (spout
   (nextTuple
    []
    (when-let [s (let [r (.getResource pool)]
                   (try
                     (.lpop r "resque:queue:rising_tide_stories")
                     (finally (.returnResource pool r))))]
      (emit-spout! collector [s])))
   (ack [id]
        ;; You only need to define this method for reliable spouts
        ;; (such as one that reads off of a queue like Kestrel)
        ;; This is an unreliable spout, so it does nothing here
        )))

(def stories (atom []))
(defn push-story! [story]
  (swap! stories (fn [stories-queue] (conj stories-queue story))))

(defspout story-spout ["story"]
  [conf context collector]
  (spout
   (nextTuple []
              (Thread/sleep 100)
              (swap! stories
                     (fn [s]
                       (if (empty? s)
                         s
                         (do
                           (emit-spout! collector [(peek s)])
                           (pop s))))))
   (ack [id]
        ;; You only need to define this method for reliable spouts
        ;; (such as one that reads off of a queue like Kestrel)
        ;; This is an unreliable spout, so it does nothing here
        )))

(defn active-users []
  [47])

(defn follow-score [user-id story]
  1)

(defn like-score [user-id story]
  1)

(defbolt active-user-bolt ["user-id" "story"] [tuple collector]
  (let [{story "story"} tuple]
    (doseq [user-id (active-users)]
      (emit-bolt! collector [user-id story])))
  (ack! collector tuple))

(defbolt like-interest-scorer ["user-id" "story" "score" "type"]  [tuple collector]
  (let [{user-id "user-id" story "story"} tuple]
    (emit-bolt! collector [user-id story (like-score user-id story) :like]))
  (ack! collector tuple))

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
                      (when (> total-score 1) (emit-bolt! collector [user-id story total-score])))
                    (swap! scores #(dissoc % [user-id story])))))
              (ack! collector tuple)))))

(defbolt add-to-feed [] {:prepare true}
  [conf context collector]
  (let [feed-set (atom {})]
    (bolt
     (execute [tuple]
              (let [{user-id "user-id" story "story" score "score"} tuple]
                (swap! feed-set #(update-in % [user-id]
                                            (fn [v] (add (or v (new-digest-feed)) story))))
                (prn "FEED SET:" (map (fn [[k v]] [k (seq v)]) @feed-set))
                (ack! collector tuple))))))

(defbolt add-to-curated-feed [] {:prepare true}
  [conf context collector]
  (let [feed (atom (new-digest-feed))]
    (bolt
     (execute [tuple]
              (let [{story "story"} tuple]
                (swap! feed #(add % story))
                (prn "EVERYTHING" (seq @feed))
                (ack! collector tuple))))))

(defn feed-generation-topology []
  (topology
   {"stories" (spout-spec story-spout)}

   ;; everything feed
   
   {"curated-feed" (bolt-spec {"stories" :global}
                              add-to-curated-feed
                              :p 1)

    ;; user feeds
    
    "active-users" (bolt-spec {"stories" :shuffle}
                   active-user-bolt
                   :p 5)
    
    "likes" (bolt-spec {"active-users" :shuffle}
                       like-interest-scorer
                       :p 20)
    "follows" (bolt-spec {"active-users" :shuffle}
                         follow-interest-scorer
                         :p 20)

    "interest-reducer" (bolt-spec {"likes" ["user-id" "story"]
                                   "follows" ["user-id" "story"]}
                                  interest-reducer
                                  :p 5)
    
    "add-to-feed" (bolt-spec {"interest-reducer" ["user-id"]}
                             add-to-feed
                             :p 20)}))

(defn run-local! []
  (let [cluster (LocalCluster.)]
    (.submitTopology cluster "story" {TOPOLOGY-DEBUG true} (feed-generation-topology))
    (Thread/sleep 10000)
    (.shutdown cluster)
    ))


(comment
  (.shutdown c)
  (def c (LocalCluster.))
  (.submitTopology c "story" {TOPOLOGY-DEBUG true} (feed-generation-topology))

  (push-story! (->ListingLikedStory :listing_shared 1 2 [3, 4] [:ev] 1))

  (let [r (.getResource pool)]
    (try
      (.lpop r "resque:queue:rising_tide_stories")
      (finally (.returnResource pool r))))

  (let [r (.getResource pool)]
    (try
      (.rpush r "resque:queue:rising_tide_stories" "{\"class\":\"Stories::AddInterestInActor\",\"args\":[47,634],\"context\":{\"log_weasel_id\":\"BROOKLYN-WEB-aef04660348f5f018d1f\"}}")
      (finally (.returnResource pool r))))
  )