(ns risingtide.storm.feed-build-bolt
  (:require [risingtide.config :as config]
            [risingtide.action.persist.solr :as solr]
            [risingtide.interests
             [brooklyn :refer [user-follows listings-for-sale]]
             [pyramid :refer [user-likes]]]
            [risingtide.storm
             [recent-actions-bolt :refer [find-recent-actions]]
             [story-bolts :refer [action-to-story]]
             [feed-bolts :refer [feed-to-json]]]
            [risingtide.model.feed.digest :refer [new-digest-feed]]

            [backtype.storm [clojure :refer [defbolt bolt emit-bolt! ack!]]]
            [metrics
             [timers :refer [deftimer time!]]
             [histograms :refer [defhistogram update!]]]))

(defbolt drpc-feed-build-bolt ["id" "user-id" "feed"] {:prepare true}
  [conf context collector]
  (let [solr-conn (solr/connection)]
    (bolt
     (execute [tuple]
              ;; the first value in the tuple coming off a drpc spout will be
              ;; the request id
              ;; the second value in the tuple coming off a drpc spout will be the
              ;; argument passed by the client
              (let [request-id (.getValue tuple 0)
                    user-id (Integer/parseInt (.getString tuple 1))
                    actions (find-recent-actions solr-conn user-id)
                    stories (map action-to-story actions)
                    feed (seq (apply new-digest-feed stories))]
                (prn "ham feed: "feed)
                (emit-bolt! collector [request-id user-id feed] :anchor tuple))
              (ack! collector tuple)))))
