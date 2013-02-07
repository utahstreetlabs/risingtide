(ns risingtide.story.scores)

(defn from-counts
  ([counts user-ids]
     (from-counts counts user-ids 1))
  ([counts user-ids coefficient]
     (dissoc
      (->> (or counts {})
           (map (fn [{cnt :cnt count :count user-id :user-id user_id :user_id}]
                  [(or user-id user_id) (* coefficient (or count cnt))]))
           (into {})
           (merge (reduce #(assoc %1 %2 0) {} user-ids)))
      nil)))

(defn sum [scores]
  (apply + (vals scores)))
