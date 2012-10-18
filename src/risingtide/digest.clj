(ns risingtide.digest
  (:use risingtide.core)
  (:require [clojure.tools.logging :as log]
            [risingtide.stories :as story]
            [risingtide.feed :as feed]
            [risingtide.key :as key]
            [risingtide.persist :as persist]
            [risingtide.shard :as shard]
            [risingtide.config :as config]
            [clojure.set :as set]))

(def ^:dynamic *card-cache-ttl* (* 6 60 60))
(def ^:dynamic *network-cache-ttl* 0)

(defn cache-ttl
  [feed-key]
  (case (last feed-key)
    \c *card-cache-ttl*
    \n *network-cache-ttl*))

(defmulti add-to-listing-digest :type)

(defmethod add-to-listing-digest "listing_multi_action" [current story]
  {:pre [(= (:listing_id current) (:listing_id story))]}
  (if (= (:actor_id current) (:actor_id story))
    (story/update-digest current
      :types (distinct (conj (:types current) (:type story)))
      :score (:score story))
    (story/multi-actor-multi-action-digest
     (:listing_id current)
     (reduce (fn [h type] (assoc h type (conj (h type) (:actor_id current))))
             {(:type story) [(:actor_id story)]}
             (:types current))
     (:score story))))

(defmethod add-to-listing-digest "listing_multi_actor" [current story]
   {:pre [(= (:listing_id current) (:listing_id story))]}
   (if (= (:type story) (:action current))
     (story/update-digest current
       :actor_ids (distinct (conj (:actor_ids current) (:actor_id story)))
       :score (:score story))
     (story/multi-actor-multi-action-digest
      (:listing_id current)
      {(:action current) (:actor_ids current)
       (:type story) [(:actor_id story)]}
      (:score story))))

(defmethod add-to-listing-digest "listing_multi_actor_multi_action" [current story]
  {:pre [(= (:listing_id current) (:listing_id story))]}
  (let [path [:types (:type story)]]
    (story/update-digest
     (assoc-in current path (distinct (conj (get-in current path) (:actor_id story))))
     :score (:score story))))

(defn- listing-digest-index-for-stories [stories]
  (reduce (fn [m story]
            (assoc m
              :actors (conj (:actors m) (:actor_id story))
              :types (conj (:types m) (:type story))))
          {:actors #{} :types #{}}
          stories))

(defn- mama-types
  [stories]
  (reduce (fn [m story] (assoc m (:type story) (conj (m (:type story)) (:actor_id story))))
          {} stories))

(defn maybe-create-new-listing-digest
  "all stories MUST have the same listing id"
  [current story]
  (let [stories (conj current story)
        index (listing-digest-index-for-stories stories)
        listing-id (:listing_id story)]
    (case [(> (count (:actors index)) 1) (> (count (:types index)) 1)]
      [true true] (story/multi-actor-multi-action-digest
                   listing-id (mama-types stories) (:score story))
      [true false] (story/multi-actor-digest listing-id (:type story) (vec (:actors index)) (:score story))
      [false true] (story/multi-action-digest listing-id (:actor_id story) (vec (:types index)) (:score story))
      [false false] stories)))

(defn- classify-current
  [current]
  (when current
    (if (story/digest-story? current)
      :digest
      (if (set? current)
        :set
        (throw (Exception. (str "corrupt cache! contains " current)))))))

(defn- listings-story-aggregator [current story]
  {:pre [(not (nil? story)) (not (nil? (:listing_id story)))]}
  (case [(classify-current current) (boolean (story/digest-story? story))]
    [nil false] #{story}
    [:digest false] (add-to-listing-digest current story)
    [:set false] (maybe-create-new-listing-digest current story)

    ;; happens when loading a predigested feed
    [nil true] story

    ;; pathological states, try to repair
    [:digest true] story #_(do (log/warn "duplicate listing digest stories! " current story "using newer") story)
    [:set true] story #_(do (log/warn "undigested and digested listing coexist! " current story "using digest") story)))

(defn add-story-to-listings-index
  [digesting-index story]
  (if (:listing_id story)
    (let [path [:listings (:listing_id story)]]
      (assoc-in digesting-index path
                (listings-story-aggregator (get-in digesting-index path) story)))
    digesting-index))

(defn add-to-actor-digest [current story]
  {:pre [(= (:actor_id current) (:actor_id story))]}
  (story/update-digest current
    :listing_ids (distinct (conj (:listing_ids current) (:listing_id story)))
    :score (:score story)))

(defn- actor-digest-index-for-stories [stories]
  (reduce (fn [m story]
            (assoc m
              :listings (conj (:listings m) (:listing_id story))))
          {:listings #{}}
          stories))

(defn maybe-create-new-actor-digest
  "all stories MUST have the same actor id"
  [current story]
  (let [stories (conj current story)
        index (actor-digest-index-for-stories stories)
        actor-id (:actor_id story)]
    (if (> (count (:listings index)) 15) ;;XXX: constant
      (story/multi-listing-digest actor-id (:type story) (vec (:listings index)) (:score story))
      stories)))

(defn actor-story-aggregator
  [current story]
  (case [(classify-current current) (boolean (story/digest-story? story))]
    [nil false] #{story}
    [:digest false] (add-to-actor-digest current story)
    [:set false] (maybe-create-new-actor-digest current story)

    ;; happens when loading a predigested feed
    [nil true] story

    ;; pathological states, try to repair
    [:digest true] story #_(do (log/warn "duplicate actor digest stories! " current story "using newer") story)
    [:set true] story #_(do (log/warn "undigested and digested actor stories coexist! " current story "using digest") story)))

(defn add-story-to-actors-index
  [digesting-index story]
  (if (:actor_id story)
    (let [path [:actors (str (:actor_id story) (:type story))]]
      (assoc-in digesting-index path
                (actor-story-aggregator (get-in digesting-index path) story)))
    digesting-index))

;;; creating a feed from the digesting index

(defn- bucket-story
  [m story]
  (if (map? story)
    (assoc m :digest (conj (:digest m) story))
    (assoc m :single (set/union (:single m) story))))

(defn feed-from-index
  [digesting-index]
  (let [bucketed-stories
        [(reduce bucket-story {} (vals (:listings digesting-index)))
         (reduce bucket-story {} (vals (:actors digesting-index)))]]
    (concat (:nodigest digesting-index)
     (apply set/union (map :digest bucketed-stories))
     (apply set/intersection (map :single bucketed-stories)))))

(defn min-story-score
  [feed-indexes]
  (let [stories (feed-from-index feed-indexes)]
    (when (not (empty? stories))
      (apply min (map :score stories)))))

(defn min-score
  [feed-indexes]
  ;; try really hard to find a min score in the indexes - theoretically there
  ;; should always be a :min key, but in case there isn't, look
  ;; through the stories we'd generate
  (or (:min feed-indexes) (min-story-score feed-indexes) (now)))

(defn update-min-score
  [feed-indexes story]
  (let [score (:score story)]
    (assoc feed-indexes :min (min score (min-score feed-indexes)))))

(defn add-story
  [digesting-index story]
  (update-min-score
   (if (and (:listing_id story) (:actor_id story))
     (-> digesting-index
         (add-story-to-listings-index story)
         (add-story-to-actors-index story))
     (assoc digesting-index :nodigest (cons story (:nodigest digesting-index))))
   story))

(defn add-predigested-story
  [digesting-index story]
  (update-min-score
   (if (story/digest-story? story)
     (if (story/listing-digest-story? story)
       (add-story-to-listings-index digesting-index story)
       (add-story-to-actors-index digesting-index story))
     (add-story digesting-index story))
   story))

(defn index-predigested-feed
  [feed]
  (reduce add-predigested-story {:listings {} :actors {}} feed))

;;; cache

(def feed-cache (atom {}))

(defn reset-cache! []
  (swap! feed-cache (constantly {}) feed-cache))

(defn get-or-load-feed-atom
  [cache-atom redii feed-key ttl]
  (or (@cache-atom feed-key)
      (let [feed-index-atom (atom (index-predigested-feed (persist/feed redii feed-key (- (now) ttl) (now))))]
        (swap! cache-atom (fn [cache] (assoc cache feed-key feed-index-atom)))
        feed-index-atom)))

(defn mark-dirty
  [feed-index]
  (assoc feed-index :dirty true))

(defn mark-atom-dirty!
  [feed-index-atom]
  (swap! feed-index-atom (fn [feed-index] (mark-dirty feed-index))))

(defn mark-feed-dirty!
  ([cache-atom feed-key]
     (when-let [feed-index-atom (@cache-atom feed-key)]
       (mark-atom-dirty! feed-index-atom)))
  ([feed-key] (mark-feed-dirty! feed-cache feed-key)))

(defn add-story-to-feed-index
  [feed-index-atom story]
  (swap! feed-index-atom (fn [feed-index] (mark-dirty (add-story feed-index story)))))

(defn add-story-to-feed-cache
  ([cache-atom redii feed-key story]
     (add-story-to-feed-index (get-or-load-feed-atom cache-atom redii feed-key (cache-ttl feed-key)) story))
  ([redii feed-key story] (add-story-to-feed-cache feed-cache redii feed-key story)))

(defn replace-feed-index!
  ([cache-atom feed-key feed-index]
     (let [new-feed-index (mark-dirty feed-index)]
       (if (@cache-atom feed-key)
         (swap! (@cache-atom feed-key) (fn [_] new-feed-index))
         (swap! cache-atom (fn [cache] (assoc cache feed-key (atom new-feed-index)))))))
  ([feed-key story] (replace-feed-index! feed-cache feed-key story)))

;;;; feed building ;;;;

(defn- fetch-filter-digest-user-stories
  [redii feed-key]
  (let [[feed-type user-id] (key/type-user-id-from-feed-key feed-key)]
    (index-predigested-feed
     (feed/user-feed-stories
      (persist/union-story-sets
       redii
       (take config/max-story-union
             (feed/interesting-story-keys feed-type user-id)) config/initial-feed-size)))))

(defn build! [redii feeds-to-build]
  (doall
   (map replace-feed-index! feeds-to-build
        (map #(fetch-filter-digest-user-stories redii %) feeds-to-build))))

;;; writing out the cache

(defn write-feed-index!
  [redii feed-key feed-indexes]
  (let [stories (feed-from-index feed-indexes)]
    (when (not (empty? stories))
      (let [low-score (min-score feed-indexes)
            high-score (now)]
        (shard/with-connection-for-feed redii feed-key
          [connection]
          (persist/replace-feed-head! connection feed-key stories low-score high-score))))))

(defn expiration-threshold
  [feed-key]
  (- (now) (cache-ttl feed-key)))

(defn expired?
  [feed-key story]
  (< (:score story) (expiration-threshold feed-key)))

(defn filter-expired-stories-from-set
  [feed-key s]
  (let [s (filter #(not (expired? feed-key %)) s)]
    (when (not (empty? s)) (into #{} s))))

(defn expire-feed-index
  [feed-key feed-index]
  (reduce (fn [m [key value]]
            (let [new-value (if (map? value)
                              (when (not (expired? feed-key value)) value)
                              (filter-expired-stories-from-set feed-key value))]
              (if new-value (assoc m key new-value) (dissoc m key))))
          {} feed-index))

(defn expire-nodigest
  [feed-key feed-indexes]
  (assoc feed-indexes :nodigest (filter-expired-stories-from-set feed-key (:nodigest feed-indexes))))

(defn expire-feed-indexes
  [feed-key feed-indexes]
  (assoc
   (expire-nodigest
    feed-key
    (reduce (fn [f key] (assoc f key (expire-feed-index feed-key (f key)))) feed-indexes [:listings :actors]))
   :min (expiration-threshold feed-key)))

(defn clean-feed-index
  [feed-key feed-index]
  (dissoc (expire-feed-indexes feed-key feed-index) :dirty))

(defn write-feed-atom!
  [redii key feed-atom]
  (when feed-atom
   (swap! feed-atom (fn [feed-index]
                      (if (:dirty feed-index)
                        (do
                          (write-feed-index! redii key feed-index)
                          (clean-feed-index key feed-index))
                        feed-index)))))

(defn write-cache!
  ([cache-atom redii]
     (doall (pmap-in-batches
             (fn [[k v]]
               (try
                 (write-feed-atom! redii k v)
                 (catch Exception e
                   (log/error (str "exception flushing cache for" k) e)
                   (safe-print-stack-trace e))))
             @cache-atom)))
  ([redii] (write-cache! feed-cache redii)))

(defn cache-flusher
  "Given a cache, a redis config and an interval in seconds, start scheduling tasks with a fixed
delay of interval to flush cached feeds to redis.
"
  [cache-atom redii interval]
  (doto (java.util.concurrent.ScheduledThreadPoolExecutor. 1)
    (.scheduleWithFixedDelay
     #(bench-if-longer-than
       50 "flushing cache"
       (try (write-cache! cache-atom redii)
            (catch Exception e
              (log/error "exception flushing cache" e)
              (safe-print-stack-trace e))))
     interval interval java.util.concurrent.TimeUnit/SECONDS)))

(defn write-feeds!
  [redii feed-keys]
  (doall (map #(write-feed-atom! redii %1 %2) feed-keys (map @feed-cache feed-keys))))

(defn build-for-user!
  [redii user-id]
  (let [keys [(key/user-card-feed user-id) (key/user-network-feed user-id)]]
    (build! redii keys)
    (write-feeds! redii keys)))

(defn initiate-migration!
  [feed-key destination-shard]
  (shard/add-migration! feed-key destination-shard)
  (mark-feed-dirty! feed-key))

(defn finalize-migration!
  [conn-spec feed-key destination-shard]
  (shard/remove-migration! feed-key)
  (let [old-shard-key (shard/shard-key conn-spec feed-key)]
    (shard/update-shard-config! conn-spec feed-key destination-shard)
    (persist/delete! (shard/shard-conn conn-spec feed-key old-shard-key) feed-key)))

(defn copy-feed!
  [feed-key from to]
  (persist/add-stories! to feed-key (persist/stories from feed-key)))

(defn migrate!
  ([conn-spec feed-key destination-shard]
     {:pre [(not (= (str destination-shard)
                    (str (shard/shard-key conn-spec feed-key))))]}
     (copy-feed! feed-key (shard/shard-conn conn-spec feed-key)
                 (shard/shard-conn conn-spec feed-key destination-shard))
     (initiate-migration! feed-key destination-shard)
     (write-feeds! conn-spec [feed-key])
     (finalize-migration! conn-spec feed-key destination-shard))
  ([conn-spec type user-id destination-shard]
     (migrate! conn-spec (key/user-feed user-id type) destination-shard)))

(defn migrate-users!
  [conn-spec type user-ids destination-shard]
  {:pre [(not (empty? conn-spec))
         (not (nil? type))]}
  (log/info (str "migrating " type " feeds for " (seq user-ids) " to " destination-shard))
  (pmap-in-batches #(migrate! conn-spec type % destination-shard) user-ids))
