(ns risingtide.integration.support
  (:require
   [clojure.data.json :as json]
   [risingtide
    [core :refer :all]
    [test :refer :all]
    [redis :as redis]
    [config :as config]
    [key :as key]]
   [risingtide.feed.persist.shard :as shard]
   [risingtide.feed.persist.shard.config :as shard-config]
   [risingtide.story.persist.solr :as solr]
   [risingtide.interests
    [brooklyn :as brooklyn]
    [pyramid :as pyramid]]))

(def conn {:everything-card-feed (redis/redis {:db 3})
           :card-feeds-1 (redis/redis {:db 4})
           :card-feeds-2 (redis/redis {:db 5})
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

#_(defn stories-about-user
  [id type]
  (map #(dissoc % :score) (stories (:stories conn)
                                   (story/actor-story-set (first-char type) id))))

;; users
#_(defmacro defuser
  [n id]
  `(do
    (def ~n ~id)
    (defn ~(symbol (str "feed-for-" n))
      [type#]
      (feed-for-user* ~id type#))
    (defn ~(symbol (str "stories-about-" n))
      [type#]
      (stories-about-user ~id type#))))

;; feeds

#_(expose persist/encode)

#_(defn encoded-feed
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
  (brooklyn/create-follow follower-id followee-id))

(defn creates-listing-like [liker-id listing-id]
  (pyramid/create-like liker-id :listing listing-id))

(defn is-a-user [user-id]
  (brooklyn/create-user user-id))

(defn clear-mysql-dbs! []
  (brooklyn/clear-tables!)
  (pyramid/clear-tables!))

(defn clear-action-solr! []
  (solr/delete-stories! (solr/connection)))

(defn builds-feeds
  [actor-id]
  #_(jobs/build-feeds! conn [actor-id]))

(defn truncates-feed
  [actor-id]
  (let [feed-key (key/user-feed actor-id)]
    (shard/with-connection-for-feed conn feed-key
      [pool]
      (redis/with-jedis* pool
        (fn [jedis] (.del jedis (into-array String [(key/user-feed actor-id)])))))))

;; reset

(defn clear-redis!
  []
  (if (= config/env :test)
    (doseq [redis [:everything-card-feed :shard-config :card-feeds-1 :card-feeds-2 :watchers :stories]]
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
