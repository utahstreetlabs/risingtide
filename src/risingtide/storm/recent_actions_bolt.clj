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

(defn find-actions [solr-conn user-id]
  (let [followee-ids (map :user_id (user-follows user-id config/recent-actions-max-follows))]
    (solr/search-interests
     solr-conn
     :rows config/recent-actions-max-recent-stories
     :actors followee-ids
     :listings (concat (filter identity (map :listing_id (user-likes user-id config/recent-actions-max-likes)))
                       (map :id (listings-for-sale followee-ids config/recent-actions-max-seller-listings))))))

(deftimer find-recent-actions-time)
(defhistogram recent-actions-found)

(defn find-recent-actions [solr-conn user-id]
  (let [actions (time! find-recent-actions-time (find-actions solr-conn user-id))]
    (update! recent-actions-found (count actions))
    actions))

(defbolt recent-actions-bolt ["id" "user-ids" "action"] {:prepare true}
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
                    actions (find-recent-actions solr-conn user-id)]
                (doseq [action actions]
                  (emit-bolt! collector [request-id user-id action] :anchor tuple)))
              (ack! collector tuple)))))
