(ns risingtide.storm.recent-stories-bolt
  (:require [risingtide.model.story :refer [->ListingLikedStory]]
            [risingtide.story.persist.solr :as solr]
            [risingtide.interests
             [brooklyn :refer [user-follows]]
             [pyramid :refer [user-likes]]]
            [backtype.storm [clojure :refer [defbolt bolt emit-bolt! ack!]]]))

(defn- find-stories [solr-conn user-id]
  (prn "FINDING FOR" user-id
       "HI"(map :user_id (user-follows user-id))
       "THERE"(filter identity (map :listing_id (user-likes user-id)))
         (solr/search-interests
   solr-conn
   :actors (map :user_id (user-follows user-id))
   :listings (filter identity (map :listing_id (user-likes user-id)))))
  (solr/search-interests
   solr-conn
   :actors (map :user_id (user-follows user-id))
   :listings (filter identity (map :listing_id (user-likes user-id)))))

(defbolt recent-stories-bolt ["id" "user-id" "action"] {:prepare true}
  [conf context collector]
  (let [solr-conn (solr/connection)]
    (bolt
     (execute [tuple]
              ;; the first value in the tuple coming off a drpc spout will be
              ;; the request id
              ;; the second value in the tuple coming off a drpc spout will be the
              ;; argument passed by the client
              (let [request-id (.getValue tuple 0)
                    user-id (.getString tuple 1)]
               (doseq [story (find-stories solr-conn user-id)]
                 (emit-bolt! collector [request-id user-id story])))
              (ack! collector tuple)))))


