(ns risingtide.integration.support
  (:use risingtide.core
        risingtide.test)
  (:require [clojure.data.json :as json]
            [accession.core :as redis]
            [risingtide.key :as key]
            [risingtide.jobs :as jobs]
            [risingtide.stories :as story]
            [risingtide.queries :as queries]
            [risingtide.digesting-cache :as dc]))

(def conn (redis/connection-map {}))

;; users
(defmacro defuser
  [name id]
  `(do
    (def ~name ~id)
    (defn ~(symbol (str "feed-for-" name))
      [type#]

      (map json/read-json
           (redis/with-connection conn
             (redis/zrange (key/user-feed ~id type#) 0 100000))))))

(defuser jim 1)
(defuser jon 2)
(defuser bcm 3)
(defuser dave 4)
(defuser rob 5)

;; profiles

(def mark-z :markz)

;; listings

(def bacon :bacon)
(def ham :ham)
(def eggs :eggs)
(def muffins :muffins)
(def breakfast-tacos :breakfast-tacos)
(def toast :toast)
(def scones :scones)
(def croissants :croissants)
(def danishes :danishes)

;; tags

(def breakfast :breakfast)

;; feeds

(defn encoded-feed
  [& stories]
  (map json/read-json (map story/encode stories)))

(defn everything-feed
  []
  (map json/read-json
       (redis/with-connection conn
         (redis/zrange (key/everything-feed) 0 1000000000))))

(def empty-feed [])

;; actions
(defmacro listing-action-helper
  [name action]
  `(defn ~name
     ([actor-id# listing-id# args#]
        (jobs/add-story! conn (merge args# (~action actor-id# listing-id#))))
     ([actor-id# listing-id#] (~name actor-id# listing-id# {}))))

(defn interested-in-user
  [actor-one-id actor-two-id]
  (jobs/add-interest! conn :actor [actor-one-id actor-two-id]))

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


;; reset

(defn clear-redis!
  []
  (if (= env :test)
    (let [keys (redis/with-connection conn (redis/keys (key/format-key "*")))]
      (when (not (empty? keys))
        (redis/with-connection conn
          (apply redis/del keys))))
   (prn "clearing redis in" env "is a super bad idea. let's not.")))

(defn clear-digesting-cache!
  []
  (dc/reset-cache!))


;; effing macros, how do they work

(defmacro with-increasing-seconds-timeline
  "In which we control time.

Within the body of this macro, risingtide.core/now will return an ever-increasing value.

Theoretically we can use midje's =streams=> for this, but it doesn't appear to be
usable in backgrounds yet.
"
  [& forms]
  `(let [current-time# (atom 1000000)
         current-time-and-advance#
         (fn []
           (let [n# @current-time#]
             (swap! current-time# inc)
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
    ~@(map swap-subject-action statements)))
