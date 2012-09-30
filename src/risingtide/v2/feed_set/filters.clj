(ns risingtide.v2.feed-set.filters)

(defn- array-wrap [v]
  (if (coll? v) v [v]))

(def user-feed-token "ylf")
(def everything-feed-token "ev")
(def default-spec [user-feed-token everything-feed-token])

(defn- feed-spec [story]
 (array-wrap (get story :feed default-spec)))

(def everything-feed-token? #{everything-feed-token})

(def user-feed-token? #{user-feed-token})

(defn user-feed-story? [story]
  (some user-feed-token? (feed-spec story)))

(defn everything-feed-story? [story]
  (some everything-feed-token? (feed-spec story)))

