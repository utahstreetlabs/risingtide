(ns risingtide.feed.filters)

;;;; filtering ;;;;

(def ev-feed-token :ev)
(def ev-feed-token? #{ev-feed-token})

(def user-feed-token :ylf)
(def user-feed-token? #{user-feed-token})

(def default-feeds [ev-feed-token user-feed-token])

(defn- for-feed-with-token?
  [story token token-pred]
  (let [f (map keyword (get story :feed default-feeds))]
    (or (empty? f) (= f token) (some token-pred f))))

(defn for-everything-feed?
  [story]
  (for-feed-with-token? story ev-feed-token ev-feed-token?))

(defn for-user-feed?
  [story]
  (for-feed-with-token? story user-feed-token user-feed-token?))

(for-user-feed? #risingtide.model.story.ListingActivatedStory{:actor-id 1, :listing-id 100, :tag-ids nil, :feed nil})