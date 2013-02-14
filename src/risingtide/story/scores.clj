(ns risingtide.story.scores)

(defn from-counts
  ([counts]
     (from-counts counts 1))
  ([counts coefficient]
     (dissoc
      (->> counts
           (map (fn [[user score]] [user (* score coefficient)]))
           (into {}))
      nil)))

(defn sum [scores]
  (apply + (vals scores)))
