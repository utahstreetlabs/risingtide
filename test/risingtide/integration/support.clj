(ns risingtide.integration.support
  (:require
   [clojure.data.json :as json]

   ;; these two must stay here due to some awkward load-time logic
   [risingtide.test :refer :all]
   [risingtide.initializers.db]

   [copious.domain
    [user :as user] [block :as block] [follow :as follow]
    [collection :as collection] [collection-follow :as collection-follow]
    [listing :as listing] [like :as like] [dislike :as dislike] [util :as domain-util]]
   [risingtide
    [core :refer :all]
    [redis :as redis]
    [config :as config]
    [key :as key]
    [active-users :refer [add-active-users]]]
   [risingtide.feed.persist :refer [encode]]
   [risingtide.feed.persist.shard :as shard]
   [risingtide.feed.persist.shard.config :as shard-config]
   [risingtide.action.persist.solr :as solr]

   [risingtide.test.support.entities :refer [collection-owners]]))

(def conn (redis/redii))

(defn stories
  ([conn key]
     (map json/read-json
          (redis/with-jedis* conn
            (fn [jedis]
              (.zrange jedis (str key) 0 100000000))))))

(defn feed-for
  [id]
  (let [feed-key (key/user-feed id)]
    (shard/with-connection-for-feed (redis/redii) feed-key
      [pool]
      (stories pool feed-key))))

(defn curated-feed
  []
  (let [feed-key (key/everything-feed)]
    (shard/with-connection-for-feed (redis/redii) feed-key
      [pool]
      (stories pool feed-key))))

(defn stories-about
  [listing-id]
  (stories (:stories (redis/redii)) (key/card-listing-story listing-id)))

;; feeds

(defn encoded-feed
  [& stories]
  (map json/read-json (map encode stories)))

(defn everything-feed
  []
  (map json/read-json
       (redis/with-jedis* (:everything-card-feed conn)
         (fn [jedis]
          (.zrange jedis (key/everything-feed) 0 1000000000)))))

(def empty-feed [])

;; actions

(defn creates-user-follow [follower-id followee-id]
  (follow/create follower-id followee-id))

(defn creates-user-block [blocker-id blockee-id]
  (block/create blocker-id blockee-id))

(defn creates-listing-dislike [disliker-id listing-id]
  (dislike/create disliker-id listing-id))

(defn creates-listing-like [liker-id listing-id]
  (like/create liker-id :listing listing-id))

(defn creates-tag-like [liker-id tag-id]
  (like/create liker-id :tag tag-id))

(defn creates-collection [collection-id owner-id]
  (collection/create collection-id owner-id))

(defn creates-collection-follow [follower-id collection-id]
  (collection-follow/create follower-id collection-id))

(defn adds-listing-to-collection [collection-id listing-id]
  (collection/create-listing-attachment collection-id listing-id))

(defn is-a-user [user-id]
  (user/create user-id))

(defn is-a-listing [listing-id seller-id]
  (listing/create listing-id seller-id))

(defn truncates-feed
  [actor-id]
  (let [feed-key (key/user-feed actor-id)]
    (shard/with-connection-for-feed conn feed-key
      [pool]
      (redis/with-jedis* pool
        (fn [jedis] (.del jedis (into-array String [(key/user-feed actor-id)])))))))

;; reset

(defn clear-mysql-dbs! []
  (if (= config/env :test)
    (domain-util/clear-tables!)
    (prn "let's not clear sql dbs in " config/env)))

(defn clear-action-solr! []
  (if (= config/env :test)
    (solr/delete-actions! (solr/connection))
    (prn "let's not clear solr in " config/env)))

(defn clear-redis!
  []
  (if (= config/env :test)
    (doseq [redis [:everything-card-feed :shard-config :card-feeds-1 :active-users]]
      (let [keys (redis/with-jedis* (redis conn) (fn [jedis] (.keys jedis (key/format-key "*"))))]
        (when (not (empty? keys))
          (redis/with-jedis* (redis conn)
            (fn [jedis] (.del jedis (into-array String keys)))))))
    (prn "clearing redis in" config/env "is a super bad idea. let's not.")))

(defn clear-digest-cache!
  []
  #_(digest/reset-cache!))

(defn clear-migrations!
  []
  (shard-config/clear-migrations!))


;; effing macros, how do they work

(def current-time (atom 1000000))

(defmacro with-increasing-seconds-timeline
  "In which we control time.

Within the body of this macro, risingtide.core/now will return an ever-increasing value.

Theoretically we can use midje's =streams=> for this, but it doesn't appear to be
usable in backgrounds yet.
"
  [& forms]
  `(let [current-time-and-advance#
         (fn []
           (let [n# @current-time]
             (swap! current-time inc)
             n#))]
     (with-redefs
       [risingtide.core/now current-time-and-advance#]
       ~@forms)))

(defn- swap-subject-action
  [statement]
  (let [[subject action & args] statement]
    (cons action (cons subject args))))

(defmacro on-copious
  "convenience macro for specifying user-action-subject actions like:

  (on-copious
   (rob interested-in-user jim)
   (jim activates bacon)
   (jim likes bacon)
   (jon likes bacon))
"
  [& statements]
  `(with-increasing-seconds-timeline
     [~@(map swap-subject-action statements)]))

(defn strip-timestamps [stories]
  (map #(dissoc % :timestamp) stories))

(defn copious-background [& {follows :follows likes :likes dislikes :dislikes listings :listings
                             blocks :blocks tag-likes :tag-likes active-users :active-users
                             collections :collections collection-follows :collection-follows}]
  (clear-mysql-dbs!)
  (clear-action-solr!)
  (clear-redis!)
  (let [users (distinct (concat (keys blocks) (vals blocks) (keys follows) (vals follows) (keys likes) (keys dislikes)
                                (keys listings) (keys tag-likes) (keys collection-follows) (vals collection-owners)))]
    (doseq [user users]
      (is-a-user user))
    (doseq [[seller-id listing-ids] listings]
      (doseq [listing-id listing-ids]
        (is-a-listing listing-id seller-id)))
    (doseq [[follower followee] follows]
      (creates-user-follow follower followee))
    (doseq [[blocker blockee] blocks]
      (creates-user-block blocker blockee))
    (doseq [[disliker listing] dislikes]
      (creates-listing-dislike disliker listing))
    (doseq [[liker listing] likes]
      (creates-listing-like liker listing))
    (doseq [[liker tag] tag-likes]
      (creates-tag-like liker tag))
    (doseq [[collection listings] collections]
      (creates-collection collection (collection-owners collection))
      (doseq [listing listings]
        (adds-listing-to-collection collection listing)))
    (doseq [[follower collections] collection-follows]
      (doseq [collection-id collections]
        (creates-collection-follow follower collection-id)))
    (apply add-active-users (redis/redii) (* 60 10) active-users)))
