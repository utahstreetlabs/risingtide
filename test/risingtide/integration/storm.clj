(ns risingtide.integration.storm
  (:require
   [risingtide.test]
   [clojure.data.json :as json]
   [risingtide
    [config :as config]
    [redis :as redis]
    [active-users :refer [add-active-users]]]
   [risingtide.storm
    [core :refer [feed-generation-topology standard-topology-config]]]
   [risingtide.model.feed.digest :refer [new-digest-feed]]
   [risingtide.model.feed :refer [remove-listing]]

   [risingtide.test.support
    [entities :refer :all]
    [stories :refer :all]
    [actions :refer :all]]
   [risingtide.integration.support :refer :all]
   [risingtide.integration.support.storm :refer [with-quiet-logs complete-topology]]
   [backtype.storm
    [config :refer :all]
    [testing :refer
     [ms= read-tuples]]]
   [midje.sweet :refer :all])
  (:import [backtype.storm LocalDRPC]))


(def drpc (LocalDRPC.))

(defn complete-feed-generation-topology [& {actions :actions feed-build-requests :feed-builds remove-requests :remove-requests
                                            :or {actions [] feed-build-requests [] remove-requests []}}]
  (complete-topology
   (feed-generation-topology drpc)
   {"actions" (map vector actions)
    "drpc-feed-build-requests" feed-build-requests
    "removals" remove-requests}))

(def jim-activated-bacon (listing-activated jim bacon rob nil nil))
(def jim-liked-ham (listing-liked jim ham cutter nil nil))
(def jim-liked-toast (listing-liked jim toast rob nil nil))
(def jim-shared-toast (listing-shared jim toast rob nil nil nil))
(def jim-saved-toast (listing-saved jim toast rob nil stuff-that-tastes-like-toast nil))
(def jim-liked-shark-board (listing-liked jim shark-board jim nil nil))
(def cutter-liked-breakfast-tacos (listing-liked cutter breakfast-tacos rob nil nil))
(def cutter-liked-muffins (listing-liked cutter muffins cutter nil nil))
(def cutter-liked-toast (listing-liked cutter toast rob nil nil))
(def rob-liked-toast (listing-liked rob toast rob nil nil))
(def travis-liked-toast (listing-liked travis toast rob nil nil))
(def cutter-liked-omelettes (listing-liked cutter omelettes travis nil nil))

(def topology-results (atom nil))

(defn run-topology [& args]
  (swap! topology-results (constantly (apply complete-feed-generation-topology args))))

(defn bolt-output
  ([name] (bolt-output name "default"))
  ([name stream] (read-tuples @topology-results name stream)))

(let [actions-rob-cares-about
      (on-copious
       (jim likes ham :sold-by cutter)
       (jim likes toast :sold-by rob)
       (jim shares toast :sold-by rob)
       (jim saves toast :to stuff-that-tastes-like-toast :sold-by rob)
       (cutter likes breakfast-tacos :sold-by rob)
       (jim likes shark-board :sold-by jim))

      actions-rob-doesnt-care-about
      (on-copious
       (cutter likes muffins :sold-by cutter)
       (travis likes toast :sold-by rob)
       (jim activates bacon :sold-by rob)
       (rob likes toast :sold-by rob)
       (cutter likes omelettes :sold-by travis))

      more-actions-rob-cares-about
      (on-copious
       (cutter likes toast :sold-by rob))

      actions (concat actions-rob-doesnt-care-about actions-rob-cares-about)]

  (against-background
    [(before :contents
             (copious-background
              :blocks {rob travis}
              :follows {rob cutter}
              :likes {rob toast}
              :dislikes {rob muffins}
              :listings {cutter [ham muffins]
                         travis [omelettes]
                         jim [shark-board rocket-board veal kitten]
                         rob [toast breakfast-tacos bacon]}
              :collections {meats-i-like [veal kitten]
                            cutterz-hot-surfboards [shark-board rocket-board]}
              :collection-follows {cutter [meats-i-like]
                                   rob [cutterz-hot-surfboards]
                                   jim [cutterz-hot-surfboards]}
              :active-users [rob])
             :after
             (do (clear-mysql-dbs!)
                 (clear-action-solr!)
                 (clear-redis!)))]

    (println "running the feed generation topology")
    (run-topology :actions actions)

    (fact "the stories bolt outputs a story for each action"
      (bolt-output "stories")
      => (contains
          [[nil nil jim-activated-bacon]
           [nil nil rob-liked-toast]
           [nil nil jim-liked-ham]
           [nil nil jim-liked-toast]
           [nil nil jim-shared-toast]
           [nil nil jim-saved-toast]
           [nil nil cutter-liked-breakfast-tacos]
           [nil nil cutter-liked-muffins]
           [nil nil jim-liked-shark-board]
           [nil nil travis-liked-toast]
           [nil nil cutter-liked-omelettes]]
          :in-any-order))

    (fact "the active users bolt outputs an active users/story pair for each action"
      (bolt-output "active-users")
      => (contains [[nil [rob] jim-activated-bacon]
                    [nil [rob] jim-liked-ham]
                    [nil [rob] jim-liked-toast]
                    [nil [rob] jim-shared-toast]
                    [nil [rob] jim-saved-toast]
                    [nil [rob] cutter-liked-breakfast-tacos]
                    [nil [rob] cutter-liked-muffins]
                    [nil [rob] jim-liked-shark-board]
                    [nil [rob] travis-liked-toast]
                    [nil [rob] cutter-liked-omelettes]]
                   :in-any-order))

    (fact "the interest reducer outputs a user/story/score tuple for each story that should be added to a feed"
      (bolt-output "interest-reducer")
      => (contains [[nil rob jim-liked-toast 1]
                    [nil rob jim-shared-toast 1]
                    [nil rob jim-saved-toast 1]
                    [nil rob cutter-liked-breakfast-tacos 1]
                    [nil rob jim-liked-ham 1]
                    [nil rob jim-liked-shark-board 1]]
                   :in-any-order))

    (fact "the save-actions bolt should output a tuple for each action"
      (strip-timestamps (map second (bolt-output "save-actions")))
      => (contains (strip-timestamps actions) :in-any-order))

    (fact "the curated feed should contain all approved stories"
      (curated-feed)
      => (contains
          (apply encoded-feed (seq (new-digest-feed jim-activated-bacon jim-liked-ham rob-liked-toast jim-liked-toast
                                                    jim-shared-toast jim-saved-toast cutter-liked-breakfast-tacos
                                                    cutter-liked-muffins jim-liked-shark-board travis-liked-toast
                                                    cutter-liked-omelettes)))
          :in-any-order))

    (fact "rob's feed should contain things rob is interested in"
      (feed-for rob)
      => (contains
          (apply encoded-feed (seq (new-digest-feed jim-liked-toast jim-shared-toast jim-saved-toast
                                                    cutter-liked-breakfast-tacos jim-liked-ham
                                                    jim-liked-shark-board)))
          :in-any-order))

    (facts "we should be able to retrieve stories about listings"
      (stories-about breakfast-tacos)
      => (encoded-feed cutter-liked-breakfast-tacos)

      (stories-about ham)
      => (encoded-feed jim-liked-ham)

      (stories-about bacon)
      => (encoded-feed jim-activated-bacon)

      ;; which story is "last" is indeterminate, but it should be one
      ;; of these
      (stories-about toast)
      => (some-checker (just (encoded-feed jim-shared-toast))
                       (just (encoded-feed jim-saved-toast))
                       (just (encoded-feed rob-liked-toast))
                       (just (encoded-feed jim-liked-toast))
                       (just (encoded-feed travis-liked-toast))))


    ;;;; test loading feeds from redis ;;;;
    ;; this resets the bolts, which zeros out in memory caches and
    ;; activates the redis-loading code
    (println "rerunning the feed generation topology with new actions")
    (run-topology :actions more-actions-rob-cares-about)

    (fact "rob's feed should have everything from the previous run, plus the new actions"
      (feed-for rob)
      => (contains
          (apply encoded-feed (seq (new-digest-feed jim-liked-toast jim-shared-toast jim-saved-toast
                                                    cutter-liked-breakfast-tacos jim-liked-ham cutter-liked-toast
                                                    jim-liked-shark-board)))
          :in-any-order))


      ;;;; test DRPC feed building ;;;;
    (println "running the feed build topology")
    (run-topology :feed-builds [[(str rob) (json/json-str {:id "12345" :host (.getServiceId drpc) :port 0})]])

    (fact "the drpc feed builder should output a feed"
      (seq (last (last (bolt-output "drpc-feed-builder" "story"))))
      => (seq (new-digest-feed jim-liked-toast jim-shared-toast jim-saved-toast cutter-liked-breakfast-tacos
                               cutter-liked-toast jim-liked-ham rob-liked-toast jim-liked-shark-board
                               ;; this next one shouldn't be in here, but we have no efficient way to get
                               ;; the sellers of listings in the drpc flow. I think we need to add
                               ;; seller_id to actions...
                               cutter-liked-omelettes)))))
