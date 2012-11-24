(ns risingtide.action.persist.solr
  (:require
   [clojure
    [set :refer [map-invert rename-keys]]
    [string :as str]
    [walk :refer [keywordize-keys]]]
   [risingtide
    [config :as config]
    [persist :refer [keywordize convert-to-kw-set convert-to-set]]]
   [clojure-solr :as solr])
  (:refer-clojure :exclude [find]))

(def to-solr-keys
  {:tag_ids :tag_id_is
   :text :comment
   :feed :feed_ss
   :buyer_id :buyer_id_i
   :network :network_s
   :type :type_s
   :timestamp :timestamp_i
   :tag_id :tag_id_i})

(def from-solr-keys (map-invert to-solr-keys))

(defn interests [action]
  [(str "a_"(:actor_id action)) (str "l_"(:listing_id action))])

(defn add-interests [action]
  (assoc action :interests
         (interests action)))

(defn encode [action]
  (-> action
      (assoc :id (or (:id action) (str (java.util.UUID/randomUUID)))
             :type (name (:type action)))
      (rename-keys to-solr-keys)
      add-interests))

(defn decode [doc]
  (-> doc
      keywordize-keys
      (rename-keys from-solr-keys)
      (dissoc :_version_ :id)
      (keywordize :type)))

(defn connection []
  (solr/connect (config/action-solr)))

(defn save! [connection & actions]
  (solr/with-connection connection
    (doseq [action actions]
      (solr/add-document!
       (encode action)))
    (solr/commit!)))


(defn- interests-string [& {actors :actors listings :listings}]
  (str/join " " (concat (map #(str "a_" %) actors)
                        (map #(str "l_" %) listings))))

(defn search-interests [conn & {actors :actors listings :listings rows :rows sort :sort}]
  (solr/with-connection conn
    (doall (map decode (solr/search (interests-string :actors actors :listings listings) :df "interests" :rows rows :sort sort)))))

(defn recent-curated-actions [conn rows]
  (solr/with-connection conn
    (doall (map decode (solr/search "ev" :df "feed_ss" :rows rows :sort "timestamp_i desc")))))

(defn find [connection id]
  (solr/with-connection connection
    (decode (first (solr/search (str "id:"id))))))

(defn delete-actions! [connection]
 (solr/with-connection connection
   (solr/delete-query!  "*:*")
   (solr/commit!)))

(comment
  (save! (connection) {:id 1 :actor_id 6 :listing_id 2 :tag_ids [1 2] :type :listing_activated :timestamp 4})
  (delete-actions! (connection))
  (search-interests (connection) :actors [1])

  )