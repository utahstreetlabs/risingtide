(ns risingtide.action.recent
  (:require [clojure.set :as set]
            [risingtide.config :as config]
            [risingtide.action.persist.solr :as solr]
            [copious.domain
             [user :as user]]
            [backtype.storm [clojure :refer [defbolt bolt emit-bolt! ack!]]]
            [metrics
             [timers :refer [deftimer time!]]
             [histograms :refer [defhistogram update!]]]))

(defn- liked-listing-ids [user-id]
  (remove nil? (map :listing_id (user/likes user-id config/recent-actions-max-likes))))

(defn- followee-listing-for-sale-ids [followee-ids]
  (map :id (user/listings-for-sale followee-ids config/recent-actions-max-seller-listings)))

(defn- disliked-listing-ids [user-id]
  (remove nil? (map :listing_id (user/dislikes user-id))))

(defn- blocked-actor-ids [user-id]
  (remove nil? (map :user_id (user/blocks user-id))))

(defn- followee-ids [user-id]
  (remove config/drpc-blacklist
          (map :user_id (user/follows user-id config/recent-actions-max-follows))))

(defn- listing-ids-from-followed-collections [user-id]
  (user/listing-ids-via-collection-follows user-id :max config/recent-actions-max-collection-follow-listings))

(defn interesting-listing-ids [user-id & {followees :followees}]
  (lazy-cat
   (listing-ids-from-followed-collections user-id)
   (liked-listing-ids user-id)
   (followee-listing-for-sale-ids (or followees (followee-ids user-id)))))

(defn find-actions [solr-conn user-id & {rows :rows sort :sort
                                         :or {rows config/recent-actions-max-recent-stories
                                              sort "timestamp_i desc"}}]
  (let [followees (followee-ids user-id)
        disliked? (set (disliked-listing-ids user-id))
        blocked? (set (blocked-actor-ids user-id))]
    ;; remove blocked users and disliked listings both before the solr
    ;; query (to get as many "good" actions as possible under the row
    ;; limit) and after the query (to weed out dislikes that came back
    ;; from the actor query and blocks that came back from the listing
    ;; query)
    (->> (solr/search-interests
          solr-conn
          :rows rows :sort sort
          :actors (remove blocked? followees)
          :listings (remove disliked?
                            (interesting-listing-ids user-id :followees followees)))
         (remove #(disliked? (:listing_id %)))
         (remove #(blocked? (:actor_id %)))
         (remove #(blocked? (:seller_id %))))))

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

