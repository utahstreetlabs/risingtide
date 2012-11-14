(ns risingtide.storm.recent-actions-bolt
  (:require [risingtide.config :as config]
            [risingtide.action.persist.solr :as solr]
            [risingtide.interests
             [brooklyn :refer [user-follows listings-for-sale]]
             [pyramid :refer [user-likes]]]
            [backtype.storm [clojure :refer [defbolt bolt emit-bolt! ack!]]]))

(defn- find-actions [solr-conn user-id]
  (let [followee-ids (map :user_id (user-follows user-id config/recent-actions-max-follows))]
    (solr/search-interests
     solr-conn
     :rows config/recent-actions-max-recent-stories
     :actors followee-ids
     :listings (concat (filter identity (map :listing_id (user-likes user-id config/recent-actions-max-likes)))
                       (map :id (listings-for-sale followee-ids config/recent-actions-max-seller-listings))))))

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
                    user-id (Integer/parseInt (.getString tuple 1))]
               (doseq [action (find-actions solr-conn user-id)]
                 (emit-bolt! collector [request-id [user-id] action] :anchor tuple)))
              (ack! collector tuple)))))

(comment

  (def solr-conn (solr/connection))
  (count
   (let [user-id 34
         followee-ids (map :user_id (user-follows user-id 100))
         actors (risingtide.core/bench "follows" (doall followee-ids))
         listings (risingtide.core/bench "likes" (doall (concat (filter identity (map :listing_id (user-likes user-id 100)))
                                                                (map :id (listings-for-sale followee-ids 100)))))]
     (risingtide.core/bench "searching"
                            (solr/search-interests
                             solr-conn
                             :rows 100
                             :actors actors
                             :listings listings)))))