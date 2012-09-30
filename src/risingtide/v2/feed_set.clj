(ns risingtide.v2.feed-set
  (require
   [risingtide.v2
    [feed :refer [new-digest-feed] :as feed]
    [watchers :refer [watchers]]]
   [risingtide.v2.feed-set.filters :refer [everything-feed-story? user-feed-story?]]))


(defprotocol FeedSet
  (add! [this story] "Add the given story to this feed set"))

;;; MemoryFeedSet implementation ;;;

(def ^:dynamic *everything-feed-key* :everything)

(defn add-to-feed! [feeds-atom key story]
  (if-let [feed-atom (get @feeds-atom key)]
    (swap! feed-atom (fn [feed] (feed/add feed story)))
    (swap! feeds-atom (fn [feeds] (assoc feeds key (feed/add (new-digest-feed) story))))))

(defn interested-feeds [story]
  (concat
   (when (everything-feed-story? story) [*everything-feed-key*])
   (when (user-feed-story? story) (watchers story))))

(deftype MemoryFeedSet [feeds]
  FeedSet
  (add! [this story]
    (doseq [watcher (watchers story)]
      (add-to-feed! feeds watcher story))
    this))
