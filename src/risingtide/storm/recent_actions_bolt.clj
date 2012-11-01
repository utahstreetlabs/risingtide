(ns risingtide.storm.recent-actions-bolt
  (:require [risingtide.action.persist.solr :as solr]
            [risingtide.interests
             [brooklyn :refer [user-follows listings-for-sale]]
             [pyramid :refer [user-likes]]]
            [backtype.storm [clojure :refer [defbolt bolt emit-bolt! ack!]]]))

(defn- find-actions [solr-conn user-id]
  (let [followee-ids (map :user_id (user-follows user-id))]
    (solr/search-interests
     solr-conn
     :actors followee-ids
     :listings (concat (filter identity (map :listing_id (user-likes user-id)))
                       (map :id (listings-for-sale followee-ids))))))

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
                 (emit-bolt! collector [request-id [user-id] action])))
              (ack! collector tuple)))))


