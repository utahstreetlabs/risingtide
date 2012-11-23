(ns risingtide.dedupe
  "Object instance deduper

Given an object, dedupe attempts to return an equivalent value that it has
seen at some point in the past.

By default, dedupe will remember a value for at least 1 minute. The expiry
can be set the first time a value is saved to dedupe - subsequent expirys for the same value
will be ignored until that value is expired from the cache.

Expiration is done by a background thread run, by default, every minute.
")

(defn now [] (.getTime (java.util.Date.)))
(def ^:private expiration-delay-key ::d)
(def ^:dynamic *default-duration* (* 60 60 1000))
(def ^:dynamic *default-expiration-delay* (* 60 1000))
(def ^:private expiry-key ::e)
(def ^:private expirer-key ::expirer)

(defn assoc-meta [obj &{:as new-meta}]
  (with-meta obj (merge (meta obj) new-meta)))

(defn new-cache
  ([expiration-delay]
     (assoc-meta {} expiration-delay-key expiration-delay))
  ([] (new-cache *default-expiration-delay*)))

(def ^:private default-cache (atom (new-cache)))

(defn reset-cache!
  ([cache]
     (swap! default-cache (fn [_] cache)))
  ([] (reset-cache! (new-cache))))

(defn expiry [obj]
  (get (meta obj) expiry-key))

(defn expired?
  ([obj time]
     (<= (expiry obj) time))
  ([obj] (expired? obj (now))))

(defn- expired-cache [cache-map]
  (apply dissoc cache-map (filter #(expired? % (now)) (keys cache-map))))

(defn expire-cache!
  ([cache]
     (swap! cache expired-cache))
  ([] (expire-cache! default-cache)))

(defn- expiration-delay [cache]
  (expiration-delay-key (meta @cache)))

(defn- run-cache-expiration! [executor cache]
  (expire-cache! cache)
  (.schedule executor #(run-cache-expiration! executor cache) (expiration-delay cache) java.util.concurrent.TimeUnit/MILLISECONDS))

(defn- expirer [cache]
  (get (meta @cache) expirer-key))

(defn- start-cache-expirer! [cache]
  (swap! cache
         #(assoc-meta %
           expirer-key (doto (java.util.concurrent.ScheduledThreadPoolExecutor. 1)
                     (run-cache-expiration! cache)))))

(defn dedupe
  "Given an object, return an equivalent object, caching the given instance
to be returned from future calls if it is not already cached."
  [obj & {for :for cache :cache :or {for *default-duration* cache default-cache}}]
  (when (not (expirer cache)) (start-cache-expirer! cache))
  (or (get @cache obj)
      (do (swap! cache #(assoc % (assoc-meta obj expiry-key (+ (now) for)) obj))
          obj)))

