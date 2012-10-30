(ns risingtide.action.persist.solr
  (:require
   [clojure
    [set :refer [map-invert rename-keys]]
    [string :as str]
    [walk :refer [keywordize-keys]]]
   [risingtide
    [config :as config]
    [persist :refer [keywordize convert-to-kw-set convert-to-set]]]
   [clojure-solr :as solr]))

(def to-solr-keys
  {:tag_ids :tag_id_is
   :text :comment
   :feed :feed_ss
   :buyer_id :buyer_id_i
   :network :network_s
   :type :type_s
   :timestamp :timestamp_i})

(def from-solr-keys (map-invert to-solr-keys))

(defn interests [action]
  [(str "a_"(:actor_id action)) (str "l_"(:listing_id action))])

(defn add-interests [action]
  (assoc action :interests
         (interests action)))

(defn strip-interests [action]
  (dissoc action :interests  :_version_))

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
      (dissoc :interests :_version_ :id)
      (keywordize :type)))

(defn connection []
  (solr/connect (config/action-solr)))

(defn save! [connection action]
  (solr/with-connection connection
    (solr/add-document!
     (encode action))
    (solr/commit!)))


(defn- interests-string [& {:as interests}]
  (str/join " " (concat (map #(str "a_" %) (:actors interests))
                        (map #(str "l_" %) (:listings interests)))))

(defn search-interests [connection & interests]
  (solr/with-connection connection
    (map decode (solr/search (apply interests-string interests) :df "interests"))))

(defn delete-actions! [connection]
 (solr/with-connection connection
   (solr/delete-query!  "*:*")
   (solr/commit!)))

(comment
  (save! (connection) {:id 1 :actor_id 6 :listing_id 2 :tag_ids [1 2] :type :listing_activated :timestamp 4})
  (delete-actions! (connection))
  (search-interests (connection))



  )