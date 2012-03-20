(ns risingtide.test)

(defn listing-story
  ([type actor-id listing-id score]
     {:type type :actor_id actor-id :listing_id listing-id :score score})
  ([type actor-id listing-id] (listing-story type actor-id listing-id nil)))

(defmacro story-helper
  [name]
  `(defn ~name
     ([actor-id# listing-id# score#]
        (listing-story ~(.replace (str name) "-" "_") actor-id# listing-id# score#))
     ([actor-id# listing-id#] (~name actor-id# listing-id# nil))))

(story-helper listing-activated)
(story-helper listing-liked)
(story-helper listing-shared)
(story-helper listing-sold)
(story-helper listing-commented)


