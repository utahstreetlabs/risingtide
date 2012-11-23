(ns risingtide.metrics)

(defn mean
  [& numbers]
  (quot (apply + numbers) (count numbers)))

(defn median [& ns]
  "Thanks, http://rosettacode.org/wiki/Averages/Median#Clojure"
  (let [ns (sort ns)
        cnt (count ns)
        mid (bit-shift-right cnt 1)]
    (if (odd? cnt)
      (nth ns mid)
      (/ (+ (nth ns mid) (nth ns (dec mid))) 2))))
