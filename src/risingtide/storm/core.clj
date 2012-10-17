(ns risingtide.storm.core
  (:require [clojure.data.json :as json]
            [risingtide.v2.story :refer [->ListingLikedStory ->ListingCommentedStory ->ListingActivatedStory]]
            [risingtide.v2.watchers :refer [watchers]]
            [risingtide.v2.feed :refer [new-digest-feed add]]
            [backtype.storm [clojure :refer :all] [config :refer :all]])
  (:import [redis.clients.jedis Jedis JedisPool JedisPoolConfig]
           [backtype.storm StormSubmitter LocalCluster]))

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

(defspout story-spout ["story"]
  [conf context collector]
  (let [sentences [(->ListingLikedStory :listing_liked 1 2 [3, 4] [:ev] 1)
                   (->ListingCommentedStory :listing_commented 1 2 [3, 4] "HI" [:ev] 1)
                   (->ListingActivatedStory :listing_activated 1 2 [3, 4] [:ev] 1)]]
    (spout
     (nextTuple []
                (Thread/sleep 1000)
                (emit-spout! collector [(rand-nth sentences)])
                )
     (ack [id]
          ;; You only need to define this method for reliable spouts
          ;; (such as one that reads off of a queue like Kestrel)
          ;; This is an unreliable spout, so it does nothing here
          ))))

(defbolt distribute-to-feeds ["user-id" "story"] [tuple collector]
  (let [story (.getValue tuple 0)]
    (doseq [user-id (watchers story)]
      (prn "sending to" user-id)
      (emit-bolt! collector [user-id story]))
    (ack! collector tuple)))

(defbolt add-to-feed [] {:prepare true}
  [conf context collector]
  (let [feed-set (atom {})]
    (bolt
     (execute [tuple]
              (let [user-id (.getValue tuple 0)
                    story (.getValue tuple 1)]
                (swap! feed-set #(update-in % user-id
                                            (fn [v] (add (or v (new-digest-feed)) story))))
                (prn "FOO" user-id "bar" feed-set)
                (ack! collector tuple))))))

(defn mk-topology []
  (topology
   {"1" (spout-spec story-spout)}
   {"2" (bolt-spec {"1" :shuffle}
                   distribute-to-feeds
                   :p 5)
    "3" (bolt-spec {"2" ["user-id"]}
                    add-to-feed
                    :p 20)}))

(defn run-local! []
  (let [cluster (LocalCluster.)]
    (.submitTopology cluster "story" {TOPOLOGY-DEBUG true} (mk-topology))
    (Thread/sleep 10000)
    (.shutdown cluster)
    ))

(comment
  (run-local!)
  (def c (LocalCluster.))
  (.submitTopology c "story" {TOPOLOGY-DEBUG true} (mk-topology))
  (.shutdown c)

  (let [r (.getResource pool)]
    (try
      (.lpop r "resque:queue:rising_tide_stories")
      (finally (.returnResource pool r))))

  (let [r (.getResource pool)]
    (try
      (.rpush r "resque:queue:rising_tide_stories" "{\"class\":\"Stories::AddInterestInActor\",\"args\":[47,634],\"context\":{\"log_weasel_id\":\"BROOKLYN-WEB-aef04660348f5f018d1f\"}}")
      (finally (.returnResource pool r))))
  )