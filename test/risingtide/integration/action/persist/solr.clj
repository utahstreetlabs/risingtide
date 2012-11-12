(ns risingtide.integration.action.persist.solr
  (:require
   [clojure.data.json :as json]
   [risingtide.action.persist.solr :as solr]
   [midje.sweet :refer :all]
   [risingtide.test]
   [risingtide.integration.support :refer :all]))

(def actions
  ["{\"listing_id\":464169,\"tag_ids\":[20,198,2476,2591,5229,14067,14070,14326,14357,210122],\"feed\":[\"ev\",\"ylf\"],\"type\":\"listing_activated\",\"actor_id\":38319}"
   "{\"listing_id\":461457,\"tag_ids\":[2421,56758,104762,145474,209789,209790],\"feed\":[\"ev\",\"ylf\"],\"text\":\"Hi, we only have brown color in stock. THANKS\",\"type\":\"listing_commented\",\"actor_id\":241324}"
   "{\"listing_id\":420636,\"tag_ids\":[8951,78080,116454,142081],\"feed\":[\"ev\",\"ylf\"],\"type\":\"listing_liked\",\"actor_id\":284245}"
   "{\"listing_id\":152958,\"tag_ids\":[20,21,63,2396,7965,13301,16420,30686,30858,50838,66135,66308,67154,67248,68646,70261,70262,185664,189428],\"feed\":[\"ev\",\"ylf\"],\"network\":\"facebook\",\"type\":\"listing_shared\",\"actor_id\":34}"
   "{\"listing_id\":463637,\"tag_ids\":[516,2946],\"feed\":[\"ylf\"],\"buyer_id\":353397,\"type\":\"listing_sold\",\"actor_id\":353706}"
   "{\"tag_id\":156689,\"type\":\"tag_liked\",\"actor_id\":354834}"])

(def solr-conn (solr/connection))

(doseq [json actions]
  (let [id (str (java.util.UUID/randomUUID))
        action (assoc (json/read-json json) :id id)]
    (solr/save! solr-conn action)

    (fact (str (action "type")" persists to solr without loss")
      (solr/find solr-conn id) => (solr/decode (solr/encode action)))))

(clear-action-solr!)