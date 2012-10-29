(ns risingtide.story.persist.solr
  (:require
   [clojure
    [set :refer [map-invert rename-keys]]
    [string :as str]
    [walk :refer [keywordize-keys]]]
   [risingtide [config :as config]]
   [clojure-solr :as solr]))

(def to-solr-keys
  {:tag_ids :tag_id_is
   :text :comment
   :feed :feed_ss
   :buyer_id :buyer_id_i
   :network :network_s})

(def from-solr-keys (map-invert to-solr-keys))

(defn interests [story]
  [(str "a_"(:actor_id story)) (str "l_"(:listing_id story))])

(defn add-interests [story]
  (assoc story :interests
         (interests story)))

(defn strip-interests [story]
  (dissoc story :interests  :_version_))

(defn encode [story]
  (-> story
      (rename-keys to-solr-keys)
      add-interests
      (assoc :id (or (:id story) (str (java.util.UUID/randomUUID))))))

(defn decode [doc]
  (-> doc
      keywordize-keys
      (rename-keys from-solr-keys)
      (dissoc :interests :_version_ :id))
  (strip-interests (rename-keys (keywordize-keys doc) from-solr-keys)))

(defn connection []
  (solr/connect (config/story-solr)))

(defn save! [connection story]
  (solr/with-connection connection
    (solr/add-document!
     (encode story))
    (solr/commit!)))


(defn- interests-string [& {:as interests}]
  (str/join " " (concat (map #(str "a_" %) (:actors interests))
                        (map #(str "l_" %) (:listings interests)))))

(defn search-interests [connection & interests]
  (solr/with-connection connection
    (map decode (solr/search (apply interests-string interests) :df "interests"))))

(comment
  (save! (connection) {:id 1 :actor_id 1 :listing_id 2 :tag_ids [1 2]})
  (search-interests (connection) :actors [1])
  )