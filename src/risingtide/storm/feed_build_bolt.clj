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
             [meters :refer [defmeter mark!]]]))

(deftimer feed-build-time)
(defmeter feed-builds "feeds built")

(defbolt drpc-feed-build-bolt ["id" "user-id" "feed"] {:prepare true}
  [conf context collector]
  (let [solr-conn (solr/connection)]
    (bolt
     (execute [{id "id" user-id "user-id" :as tuple}]
              (time! feed-build-time
               (let [actions (find-recent-actions solr-conn user-id)
                     stories (map action-to-story actions)
                     feed (seq (apply new-digest-feed stories))]
                 (mark! feed-builds)
                 (emit-bolt! collector [id user-id feed])))
              (ack! collector tuple)))))
