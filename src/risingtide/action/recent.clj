(ns risingtide.action.recent
  (:require [clojure.set :as set]
            [risingtide.config :as config]
            risingtide.initializers.db
            [risingtide.action.persist.solr :as solr]
            [copious.domain
             [user :as user]]
            [backtype.storm [clojure :refer [defbolt bolt emit-bolt! ack!]]]
            [metrics
             [timers :refer [deftimer time!]]
             [histograms :refer [defhistogram update!]]]))

(defn- followee-ids [user-id]
  (remove config/drpc-blacklist
          (map :user_id (user/follows user-id config/recent-actions-max-follows))))

(defn- disliked-listing-ids [user-id]
  (remove nil? (map :listing_id (user/dislikes user-id))))

(defn- blocked-actor-ids [user-id]
  (remove nil? (map :user_id (user/blocks user-id))))

(defn- listing-ids-from-followed-collections [user-id]
  (user/listing-ids-via-collection-follows user-id :max config/recent-actions-max-collection-follow-listings))

(defn- liked-listing-ids [user-id]
  (remove nil? (map :listing_id (user/likes user-id config/recent-actions-max-likes))))

(defn- followee-listing-for-sale-ids [followee-ids]
  (map :id (user/listings-for-sale followee-ids config/recent-actions-max-seller-listings)))

(deftimer recent-collection-listing-ids-time)
(deftimer recent-liked-listing-ids-time)
(deftimer recent-followee-listing-ids-time)

(defn interesting-listing-ids [user-id & {followees :followees}]
  ;; speculatively perform these requests, but only wait for their
  ;; result if it is needed
  (let [collection (future (time! recent-collection-listing-ids-time
                                  (listing-ids-from-followed-collections user-id)))
        liked (future (time! recent-liked-listing-ids-time
                             (liked-listing-ids user-id)))
        followee (future (time! recent-followee-listing-ids-time
                                (followee-listing-for-sale-ids (if followees @followees (followee-ids user-id)))))]
    (lazy-cat @collection @liked @followee)))

(deftimer recent-followee-ids-time)
(deftimer recent-actor-ids-time)
(deftimer recent-listing-ids-time)
(deftimer recent-search-interests-time)

(defn find-actions [solr-conn user-id & {rows :rows sort :sort
                                         :or {rows config/recent-actions-max-recent-stories
                                              sort "timestamp_i desc"}}]
  (let [followees (future (time! recent-followee-ids-time (followee-ids user-id)))
        disliked? (future (set (disliked-listing-ids user-id)))
        blocked? (future (set (blocked-actor-ids user-id)))
        listing-ids (future (interesting-listing-ids user-id :followees followees))
        actors (time! recent-actor-ids-time
                      (doall
                       (take config/recent-actions-max-actors
                             (remove @blocked? @followees))))
        listings (time! recent-listing-ids-time
                        (doall
                         (take config/recent-actions-max-listings
                               (remove @disliked? @listing-ids))))]
    ;; remove blocked users and disliked listings both before the solr
    ;; query (to get as many "good" actions as possible under the row
    ;; limit) and after the query (to weed out dislikes that came back
    ;; from the actor query and blocks that came back from the listing
    ;; query)
    (->> (time! recent-search-interests-time
                (solr/search-interests
                 solr-conn
                 :rows rows :sort sort
                 ;; limit both of these to avoid blowing up lucene - too many
                 ;; boolean queries makes it explode. ~1k is a rough maximum
                 :actors actors
                 :listings listings))
         (remove #(@disliked? (:listing_id %)))
         (remove #(@blocked? (:actor_id %)))
         (remove #(@blocked? (:seller_id %))))))

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
