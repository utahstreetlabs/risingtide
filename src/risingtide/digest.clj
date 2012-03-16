(ns risingtide.digest)

(defn multi-action-digest-story
  [listing-id actor-id actions]
  {:type "listing_multi_action" :actor_id actor-id :listing_id listing-id :actions actions})

(defn multi-actor-digest-story
  [listing-id action actor-ids]
  {:type "listing_multi_actor" :listing_id listing-id :action action :actor_ids actor-ids})

(defn multi-actor-multi-action-digest-story
  [listing-id & [actions]]
  {:type "listing_multi_actor_multi_action" :listing_id listing-id :actions actions})

(defn multi-listing-digest-story
  [actor-id action & listing-ids]
  {:type "actor_multi_listing" :actor_id actor-id :action action :listing_ids listing-ids})

(defn digest-story
  [digested story]
  (assoc-in digested [:listings (story :listing_id) (story :type) (story :actor_id)] story))

(defn single-listing-digest-story
  [[listing-id actions]]
  (if (= 1 (count actions))
    (let [[action actors] (first actions)]
      (if (= 1 (count actors))
        (second (first actors))
        (multi-actor-digest-story listing-id action (keys actors))))
    (let [actors (distinct (flatten (map keys (vals actions))))]
      (if (= 1 (count actors))
        (multi-action-digest-story listing-id (first actors) (keys actions))
        (multi-actor-multi-action-digest-story
         listing-id
         (reduce (fn [m [action actors]] (assoc m action (keys actors))) {} actions))))))

(defn digest
  [feed]
  (let [digested (reduce digest-story {} feed)]
    (let [single-listing-stories (map single-listing-digest-story (:listings digested))
          ;;TODO single-actor-stories (map single-actor-digest-story (:actors digested))
          ]
      single-listing-stories)))
