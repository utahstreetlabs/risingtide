(ns risingtide.integration.support
  (:use [risingtide.core])
  (:require [clojure.data.json :as json]
            [accession.core :as redis]
            [risingtide.key :as key]
            [risingtide.jobs :as jobs]
            [risingtide.stories :as story]
            [risingtide.queries :as queries]
            [risingtide.digesting-cache :as dc]))

(def conn (redis/connection-map {}))

;; users
(json/read-json "[\"b\", \"a\"]")
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

;; listings

(def bacon 10)
(def ham 11)
(def eggs 12)
(def muffins 13)

;; stories

(defn listing-story
  [listing-id actor-id type]
  {:listing_id listing-id :actor_id actor-id :type type})

(defn listing-activated
  [listing-id actor-id]
  (listing-story listing-id actor-id "listing_activated"))

(defn listing-liked
  [listing-id actor-id]
  (listing-story listing-id actor-id "listing_liked"))

(defn listing-shared
  [listing-id actor-id]
  (listing-story listing-id actor-id "listing_shared"))

(defn listing-sold
  [listing-id actor-id]
  (listing-story listing-id actor-id "listing_sold"))

(defn listing-commented
  [listing-id actor-id]
  (listing-story listing-id actor-id "listing_commented"))

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

(defn interested-in-user
  [actor-one-id actor-two-id]
  (jobs/add-interest! conn :actor [actor-one-id actor-two-id]))

(defn activates
  [actor-id listing-id]
  (jobs/add-story! conn (listing-activated listing-id actor-id)))

(defn likes
  [actor-id listing-id]
  (jobs/add-story! conn (listing-liked listing-id actor-id)))

(defn shares
  [actor-id listing-id]
  (jobs/add-story! conn (listing-shared listing-id actor-id)))

(defn sells
  [actor-id listing-id]
  (jobs/add-story! conn (listing-sold listing-id actor-id)))

(defn comments-on
  [actor-id listing-id]
  (jobs/add-story! conn (listing-commented listing-id actor-id)))

;; reset

(defn clear-redis!
  []
  (let [keys (redis/with-connection conn (redis/keys "*"))]
    (when (not (empty? keys))
      (redis/with-connection conn
        (apply redis/del keys)))))

(defn clear-digesting-cache!
  []
  (dc/reset-cache!))


;; effing macros, how do they work

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
  `(do
     ~@(map swap-subject-action statements)))

