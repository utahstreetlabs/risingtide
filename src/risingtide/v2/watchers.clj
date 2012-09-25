(ns risingtide.v2.watchers
  (require [risingtide.v2.story]
           [risingtide.key :as key])
  (import [risingtide.v2.story ListingSharedStory ListingSoldStory ListingActivatedStory ListingCommentedStory ListingLikedStory TagLikedStory]))

(defn actor-watcher-keys [story]
  [(key/actor-watchers (:actor-id story))])

(defn listing-watcher-keys [story]
  [(key/listing-watchers (:listing-id story))])

(defn tag-watcher-keys [story]
  (map key/tag-watchers (:tag-ids story)))

(defn listing-story-watcher-keys [story]
  (concat (actor-watcher-keys story) (listing-watcher-keys story)))

(defprotocol Watchers
  (watcher-keys [story]))

(extend-protocol Watchers
  TagLikedStory (watcher-keys [story] (actor-watcher-keys story))
  ListingLikedStory (watcher-keys [story] (listing-story-watcher-keys story))
  ListingCommentedStory (watcher-keys [story] (listing-story-watcher-keys story))
  ListingSharedStory (watcher-keys [story] (listing-story-watcher-keys story))
  ListingActivatedStory (watcher-keys [story] (concat (listing-story-watcher-keys story) (tag-watcher-keys story)))
  ListingSoldStory (watcher-keys [story] (concat (listing-story-watcher-keys story) (tag-watcher-keys story))))

;; make me compile
#_(defn watchers [redii story]
  (redis/with-jedis* (:interests redii)
    (fn [jedis] (.sunion jedis (into-array String (watcher-keys keys))))))