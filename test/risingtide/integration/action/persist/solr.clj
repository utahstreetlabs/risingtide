(ns risingtide.integration.action.persist.solr
  (:require
   [clojure.data.json :as json]
   [risingtide.action.persist.solr :as solr]
   [risingtide.storm
    [story-bolts :refer [action-to-story]]]
   [midje.sweet :refer :all]
   [risingtide.test]
   [risingtide.integration.support :refer :all]
   [risingtide.test :refer [serialize-deserialize]]
   [clj-time.core :refer [months ago]]))

(def actions
  ["{\"listing_id\":464169,\"tag_ids\":[20,198,2476,2591,5229,14067,14070,14326,14357,210122],\"feed\":[\"ev\",\"ylf\"],\"type\":\"listing_activated\",\"actor_id\":38319,\"seller_id\":353707}"

   "{\"listing_id\":420636,\"tag_ids\":[8951,78080,116454,142081],\"feed\":[\"ev\",\"ylf\"],\"type\":\"listing_liked\",\"actor_id\":284245,\"seller_id\":353707}"
   "{\"listing_id\":152958,\"tag_ids\":[20,21,63,2396,7965,13301,16420,30686,30858,50838,66135,66308,67154,67248,68646,70261,70262,185664,189428],\"feed\":[\"ev\",\"ylf\"],\"network\":\"facebook\",\"type\":\"listing_shared\",\"actor_id\":34,\"seller_id\":353707}"
   "{\"listing_id\":463637,\"tag_ids\":[516,2946],\"feed\":[\"ylf\"],\"buyer_id\":353397,\"type\":\"listing_sold\",\"actor_id\":353706,\"seller_id\":353707}"
   "{\"listing_id\":463637,\"tag_ids\":[516,2946],\"feed\":[\"ylf\"],\"collection_id\":4,\"type\":\"listing_saved\",\"actor_id\":353706,\"seller_id\":353707}"
   "{\"tag_id\":156689,\"type\":\"tag_liked\",\"actor_id\":354834}"])

(def solr-conn (solr/connection))


(clear-action-solr!)

(doseq [json actions]
  (let [id (str (java.util.UUID/randomUUID))
        action (assoc (json/read-json json) :id id)
        result-action (dissoc (solr/decode (solr/encode action)) :interests)]
    (solr/save! solr-conn action)

    (fact (str (action "type")" persists to solr without loss")
      (solr/find solr-conn id) => result-action)

    (fact (str (action "type")" can be kryo-serialized after saving to solr")
      (serialize-deserialize (action-to-story (solr/find solr-conn id))) => (action-to-story result-action))))

(clear-action-solr!)


(defn get-all-timestamps []
  (map #(get % "timestamp_i")
       (clojure-solr/with-connection solr-conn
         (clojure-solr/search (str "timestamp_i:[0 TO "(-> 0 months ago solr/timestamp)"]")))))

(let [one-minute-ago-action {:timestamp_i (-> 1 months ago solr/timestamp) :listing_id 1 :tag_ids [] :type "listing_liked"}
      ten-minutes-ago-action {:timestamp_i (-> 10 months ago solr/timestamp) :listing_id 1 :tag_ids [] :type "listing_liked"}
      thirty-minutes-ago-action {:timestamp_i (-> 30 months ago solr/timestamp) :listing_id 1 :tag_ids [] :type "listing_liked"}]
  (against-background
   [(before :facts
            (do (clear-action-solr!)
                (solr/save! solr-conn one-minute-ago-action)
                (solr/save! solr-conn ten-minutes-ago-action)
                (solr/save! solr-conn thirty-minutes-ago-action)))
    (after :facts (clear-action-solr!))]

   (fact
     (get-all-timestamps)
     => (map :timestamp_i [one-minute-ago-action ten-minutes-ago-action thirty-minutes-ago-action]))

   (fact
     (do (solr/delete-actions-older-than! solr-conn (-> 5 months))
         (get-all-timestamps))
     => (map :timestamp_i [one-minute-ago-action]))))

