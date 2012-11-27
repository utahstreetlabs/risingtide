(ns risingtide.storm.recent-actions-bolt
  (:require [risingtide.config :as config]
            [risingtide.action.persist.solr :as solr]
            [risingtide.interests
             [brooklyn :refer [user-follows listings-for-sale]]
             [pyramid :refer [user-likes]]]
            [backtype.storm [clojure :refer [defbolt bolt emit-bolt! ack!]]]
            [metrics
             [timers :refer [deftimer time!]]
             [histograms :refer [defhistogram update!]]]))

(defn find-actions [solr-conn user-id & {rows :rows sort :sort
                                         :or {rows config/recent-actions-max-recent-stories
                                              sort "timestamp_i desc"}}]
  (let [followee-ids (filter (comp not config/drpc-blacklist) (map :user_id (user-follows user-id config/recent-actions-max-follows)))]
    (solr/search-interests
     solr-conn
     :rows rows
     :sort sort
     :actors followee-ids
     :listings (concat (filter identity (map :listing_id (user-likes user-id config/recent-actions-max-likes)))
                       (map :id (listings-for-sale followee-ids config/recent-actions-max-seller-listings))))))

(deftimer find-recent-actions-time)
(defhistogram recent-actions-found)

(defn backfill-actions [solr-conn actions]
  (let [shortfall (- config/minimum-drpc-actions (count actions))]
    (if (> shortfall 0)
      (solr/recent-curated-actions solr-conn shortfall)
      [])))

(defn find-recent-actions [solr-conn user-id]
  (let [actions (time! find-recent-actions-time (find-actions solr-conn user-id))]
    (update! recent-actions-found (count actions))
    (concat actions (backfill-actions solr-conn actions))))

