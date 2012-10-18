(ns risingtide.integration.support
  (:use risingtide.core
        risingtide.test)
  (:require [clojure.data.json :as json]
            [risingtide
             [redis :as redis]
             [config :as config]
             [key :as key]
             [jobs :as jobs]
             [persist :as persist]
             [stories :as story]
             [shard :as shard]
             [digest :as digest]]
            [risingtide.shard.config :as shard-config]
            [risingtide.interests
             [brooklyn :as brooklyn]
             [pyramid :as pyramid]]))

(def conn {:watchers (redis/redis {:db 2})
           :everything-card-feed (redis/redis {:db 3})
           :card-feeds-1 (redis/redis {:db 4})
           :card-feeds-2 (redis/redis {:db 5})
           :network-feeds (redis/redis {:db 6})
           :stories (redis/redis {:db 7})
           :shard-config (redis/redis {:db 8})})

(defn stories
  ([conn key]
     (map json/read-json
          (redis/with-jedis* conn
            (fn [jedis]
              (.zrange jedis key 0 100000000)))))
  ([conn id type] (stories conn (key/user-feed id type))))

(defn feed-for-user*
  [id type]
  (let [feed-key (key/user-feed id type)]
    (shard/with-connection-for-feed conn feed-key
      [pool]
      (stories pool feed-key))))

(defn stories-about-user
  [id type]
  (map #(dissoc % :score) (stories (:stories conn)
                                   (story/actor-story-set (first-char type) id))))

;; users
(defmacro defuser
  [n id]
  `(do
    (def ~n ~id)
    (defn ~(symbol (str "feed-for-" n))
      [type#]
      (feed-for-user* ~id type#))
    (defn ~(symbol (str "stories-about-" n))
      [type#]
      (stories-about-user ~id type#))))

(defuser jim 1)
(defuser jon 2)
(defuser bcm 3)
(defuser dave 4)
(defuser rob 5)
(defuser cutter 6)
(defuser kaitlyn 7)
(defuser courtney 8)


;; profiles

(def mark-z :markz)

;; listings

(def bacon 100)
(def ham 101)
(def eggs 102)
(def muffins 103)
(def breakfast-tacos 104)
(def toast 105)
(def scones 106)
(def croissants 107)
(def danishes 108)
(def omelettes 109)
(def nail-polish 110)

;; tags

(def breakfast 200)

;; feeds

(expose persist/encode)

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
(defn interested-in-user [actor-one-id actor-two-id]
  (jobs/add-interest! conn :actor [actor-one-id actor-two-id]))

(defn interested-in-listing [actor-id listing-id]
  (jobs/add-interest! conn :listing [actor-id listing-id]))

(defn removes-interest-in-listings [actor-id & listing-ids]
  (jobs/batch-remove-user-interests! conn :listing [actor-id listing-ids]))

(defn creates-brooklyn-follow [follower-id followee-id]
  (brooklyn/create-follow follower-id followee-id))

(defn creates-listing-like [liker-id listing-id]
  (pyramid/create-like liker-id :listing listing-id))

(defn is-a-user [user-id]
  (brooklyn/create-user user-id))

(defn clear-mysql-dbs! []
  (brooklyn/clear-tables!)
  (pyramid/clear-tables!))

(defmacro listing-action-helper
  [name action]
  `(defn ~name
     ([actor-id# listing-id# args#]
        (jobs/add-story! conn (merge args# (~action actor-id# listing-id#))))
     ([actor-id# listing-id#] (~name actor-id# listing-id# {}))))

(listing-action-helper activates listing-activated)
(listing-action-helper likes listing-liked)
(listing-action-helper shares listing-shared)
(listing-action-helper sells listing-sold)
(listing-action-helper comments-on listing-commented)

(defn likes-tag
  [actor-id tag-id]
  (jobs/add-story! conn (tag-liked actor-id tag-id)))

(defn joins
  [actor-id]
  (jobs/add-story! conn (user-joined actor-id)))

(defn follows
  [actor-id followee-id]
  (jobs/add-story! conn (user-followed actor-id followee-id)))

(defn invites
  [actor-id invitee-profile-id]
  (jobs/add-story! conn (user-invited actor-id invitee-profile-id)))

(defn piles-on
  [actor-id invitee-profile-id]
  (jobs/add-story! conn (user-piled-on actor-id invitee-profile-id)))

(defn activates-many-listings
  [actor-id ids]
  (doseq [id ids]
    (activates actor-id id)))

(defn builds-feeds
  [actor-id]
  (jobs/build-feeds! conn [actor-id]))

(defn truncates-feed
  [actor-id]
  (let [feed-key (key/user-card-feed actor-id)]
    (shard/with-connection-for-feed conn feed-key
      [pool]
      (redis/with-jedis* pool
        (fn [jedis] (.del jedis (into-array String [(key/user-card-feed actor-id)])))))))

;; reset

(defn clear-redis!
  []
  (if (= env :test)
    (doseq [redis [:everything-card-feed :shard-config :card-feeds-1 :card-feeds-2 :network-feeds :watchers :stories]]
      (let [keys (redis/with-jedis* (redis conn) (fn [jedis] (.keys jedis (key/format-key "*"))))]
        (when (not (empty? keys))
          (redis/with-jedis* (redis conn)
            (fn [jedis] (.del jedis (into-array String keys)))))))
    (prn "clearing redis in" env "is a super bad idea. let's not.")))

(defn clear-digest-cache!
  []
  (digest/reset-cache!))

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

(def write! digest/write-cache!)

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
     ~@(map swap-subject-action statements)
     (write! conn)))

