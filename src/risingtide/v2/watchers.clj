(ns risingtide.v2.watchers
  (require risingtide.v2.story
           [risingtide.key :as key]
           [risingtide.v2.watchers.persist :as persist]))

(defn actor-watcher-keys [story]
  [(key/actor-watchers (:actor-id story))])

(defn listing-watcher-keys [story]
  [(key/listing-watchers (:listing-id story))])

(defn tag-watcher-keys [story]
  (map key/tag-watchers (:tag-ids story)))

(defn listing-story-watcher-keys [story]
  (concat (actor-watcher-keys story) (listing-watcher-keys story)))

(defn tag-listing-story-watcher-keys [story]
  (concat (listing-story-watcher-keys story) (tag-watcher-keys story)))

(defprotocol Watchers
  (watcher-keys [story]))

(extend-protocol Watchers
  risingtide.v2.story.TagLikedStory
  (watcher-keys [story] (actor-watcher-keys story))
  risingtide.v2.story.ListingLikedStory
  (watcher-keys [story] (listing-story-watcher-keys story))
  risingtide.v2.story.ListingCommentedStory
  (watcher-keys [story] (listing-story-watcher-keys story))
  risingtide.v2.story.ListingSharedStory
  (watcher-keys [story] (listing-story-watcher-keys story))
  risingtide.v2.story.ListingActivatedStory
  (watcher-keys [story] (tag-listing-story-watcher-keys story))
  risingtide.v2.story.ListingSoldStory
  (watcher-keys [story] (tag-listing-story-watcher-keys story)))

(defn watchers [story]
  (persist/get-watchers (watcher-keys story)))
