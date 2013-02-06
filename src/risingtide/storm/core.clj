(ns risingtide.storm.core
  (:require [risingtide.config :as config]
            risingtide.initializers.db
            [risingtide.storm
             [action-spout :refer [action-spout]]
             [remove-spout :refer [remove-spout]]
             [remove-bolts :refer [prepare-removals]]
             [story-bolts :refer [create-story-bolt save-story-bolt]]
             [action-bolts :refer [prepare-action-bolt save-action-bolt]]
             [active-user-bolt :refer [active-user-bolt]]
             [interests-bolts :refer [like-interest-scorer follow-interest-scorer
                                      tag-like-interest-scorer dislike-interest-scorer
                                      seller-follow-interest-scorer interest-reducer
                                      collection-follow-interest-scorer
                                      block-interest-scorer seller-block-interest-scorer]]
             [feed-bolts :refer [add-to-feed add-to-curated-feed]]
             [build-feed :as feed-building]]
            [risingtide.storm.drpc.local-server :as local-drpc-server]
            [backtype.storm [clojure :refer [defbolt bolt emit-bolt! ack! topology spout-spec bolt-spec]] [config :refer :all]]))

(defbolt drpc-acker ["id" "user-id" "feed"] [{id "id" user-id "user-id" feed "feed" :as tuple} collector]
  (emit-bolt! collector [id user-id feed])
  (ack! collector tuple))

(def standard-topology-config
  {TOPOLOGY-FALL-BACK-ON-JAVA-SERIALIZATION false
   TOPOLOGY-KRYO-REGISTER
   [{"risingtide.model.story.TagLikedStory" "risingtide.serializers.TagLikedStory"}
    {"risingtide.model.story.ListingLikedStory" "risingtide.serializers.ListingLikedStory"}
    {"risingtide.model.story.ListingCommentedStory" "risingtide.serializers.ListingCommentedStory"}
    {"risingtide.model.story.ListingActivatedStory" "risingtide.serializers.ListingActivatedStory"}
    {"risingtide.model.story.ListingSoldStory" "risingtide.serializers.ListingSoldStory"}
    {"risingtide.model.story.ListingSharedStory" "risingtide.serializers.ListingSharedStory"}
    {"risingtide.model.story.ListingSavedStory" "risingtide.serializers.ListingSavedStory"}
    {"risingtide.model.story.MultiActorMultiActionStory" "risingtide.serializers.MultiActorMultiActionStory"}
    {"risingtide.model.story.MultiActorStory" "risingtide.serializers.MultiActorStory"}
    {"risingtide.model.story.MultiActionStory" "risingtide.serializers.MultiActionStory"}
    {"risingtide.model.story.MultiListingStory" "risingtide.serializers.MultiListingStory"}]})

(def p config/parallelism)

(defn feed-generation-topology
  ([] (feed-generation-topology nil))
  ([drpc]
     (topology
      (merge {"actions" (spout-spec action-spout)
              "removals" (spout-spec remove-spout)}
             (feed-building/spouts drpc))

      (merge
       {"prepare-removals" (bolt-spec {"removals" :shuffle}
                                      prepare-removals
                                     :p (p :prepare-removals))

        "prepare-actions" (bolt-spec {"actions" :shuffle}
                                     prepare-action-bolt
                                     :p (p :prepare-actions))

        "save-actions" (bolt-spec {"prepare-actions" :shuffle}
                                  save-action-bolt)

        "stories" (bolt-spec {"prepare-actions" :shuffle}
                             create-story-bolt
                             :p (p :stories))

        ;; everything feed
        "curated-feed" (bolt-spec {"stories" :global}
                                  add-to-curated-feed)

        ;; search and browse cache
        "search-and-browse" (bolt-spec {"stories" :global}
                                       save-story-bolt)

        ;; user feeds

        "active-users" (bolt-spec {"stories" :shuffle}
                                  active-user-bolt
                                  :p (p :active-users))

        "likes" (bolt-spec {"active-users" :shuffle}
                           like-interest-scorer
                           :p (p :likes))
        "tag-likes" (bolt-spec {"active-users" :shuffle}
                               tag-like-interest-scorer
                               :p (p :tag-likes))
        "follows" (bolt-spec {"active-users" :shuffle}
                             follow-interest-scorer
                             :p (p :follows))
        "blocks" (bolt-spec {"active-users" :shuffle}
                            block-interest-scorer
                            :p (p :blocks))
        "seller-follows" (bolt-spec {"active-users" :shuffle}
                                    seller-follow-interest-scorer
                                    :p (p :seller-follows))
        "seller-blocks" (bolt-spec {"active-users" :shuffle}
                                   seller-block-interest-scorer
                                   :p (p :seller-blocks))
        "collection-follows" (bolt-spec {"active-users" :shuffle}
                                        collection-follow-interest-scorer
                                        :p (p :collection-follows))
        "dislikes" (bolt-spec {"active-users" :shuffle}
                              dislike-interest-scorer
                              :p (p :seller-follows))

        "interest-reducer" (bolt-spec {"likes" ["user-ids-hash"]
                                       "tag-likes" ["user-ids-hash"]
                                       "blocks" ["user-ids-hash"]
                                       "follows" ["user-ids-hash"]
                                       "dislikes" ["user-ids-hash"]
                                       "seller-follows" ["user-ids-hash"]
                                       "seller-blocks" ["user-ids-hash"]
                                       "collection-follows" ["user-ids-hash"]}
                                      interest-reducer
                                      :p (p :interest-reducer))

        "drpc-acker" (bolt-spec {["drpc-feed-builder" "story"] :shuffle}
                                drpc-acker)

        "add-to-feed" (bolt-spec {"interest-reducer" ["user-id"]
                                  "drpc-acker" ["user-id"]
                                  "prepare-removals" ["user-id"]}
                                 add-to-feed
                                 :p (p :add-to-feed))}
       (feed-building/bolts)))))
