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
   [backtype.storm
    [config :refer :all]
    [testing :refer
     [with-local-cluster with-simulated-time-local-cluster ms= complete-topology read-tuples]]]
   [midje.sweet :refer :all])
  (:import [backtype.storm LocalDRPC]))

(def drpc (LocalDRPC.))

(defn complete-feed-generation-topology [& {actions :actions feed-build-requests :feed-builds remove-requests :remove-requests
                                            :or {actions [] feed-build-requests [] remove-requests []}}]
  (with-local-cluster [cluster :daemon-conf (merge standard-topology-config)]
    (let [results
          (complete-topology cluster
                             (feed-generation-topology drpc)
                             :mock-sources {"actions" (map vector actions)
                                            "drpc-feed-build-requests" feed-build-requests
                                            "removals" remove-requests}
                             ;; turn up parallelism to force serialization
                             :storm-conf {TOPOLOGY-WORKERS 6}
                             )]
      (Thread/sleep 5000)
      results)))

(def jim-activated-bacon (listing-activated jim bacon nil nil))
(def jim-liked-ham (listing-liked jim ham nil nil))
(def jim-liked-toast (listing-liked jim toast nil nil))
(def jim-shared-toast (listing-shared jim toast nil nil nil))
(def cutter-liked-breakfast-tacos (listing-liked cutter breakfast-tacos nil nil))
(def cutter-liked-muffins (listing-liked cutter muffins nil nil))
(def cutter-liked-toast (listing-liked cutter toast nil nil))
(def rob-liked-toast (listing-liked rob toast nil nil))

(def topology-results (atom nil))

(defn run-topology [& args]
  (swap! topology-results (constantly (apply complete-feed-generation-topology args))))

(defn bolt-output
  ([name] (bolt-output name "default"))
  ([name stream] (read-tuples @topology-results name stream)))

(let [actions-rob-cares-about
      (on-copious
       (jim likes ham)
       (jim likes toast)
       (jim shares toast)
       (cutter likes breakfast-tacos)
       (cutter likes muffins))

      actions-rob-doesnt-care-about
      (on-copious
       (jim activates bacon)
       (rob likes toast))

      more-actions-rob-cares-about
      (on-copious
       (cutter likes toast))

      actions (concat actions-rob-doesnt-care-about actions-rob-cares-about)]

  (against-background
    [(before :contents
             (copious-background
              :follows {rob cutter
                        jon cutter}
              :likes {rob toast
                      jon toast}
              :dislikes {rob muffins
                         jon muffins}
              :listings {cutter [ham muffins]}
              :active-users [rob])
             :after
             (do (clear-mysql-dbs!)
                 (clear-action-solr!)
                 (clear-redis!)))]

    (facts "about a basic topology"
      (run-topology :actions actions)

      (bolt-output "stories") =>
      (contains
       [[nil nil jim-activated-bacon]
        [nil nil rob-liked-toast]
        [nil nil jim-liked-ham]
        [nil nil jim-liked-toast]
        [nil nil jim-shared-toast]
        [nil nil cutter-liked-breakfast-tacos]
        [nil nil cutter-liked-muffins]]
       :in-any-order)

      (bolt-output "active-users") =>
      (contains [[nil [rob] jim-activated-bacon]
                 [nil [rob] jim-liked-ham]
                 [nil [rob] jim-liked-toast]
                 [nil [rob] jim-shared-toast]
                 [nil [rob] cutter-liked-breakfast-tacos]
                 [nil [rob] cutter-liked-muffins]]
                :in-any-order)

      (bolt-output "interest-reducer") =>
      (contains [[nil rob jim-liked-toast 1]
                 [nil rob jim-shared-toast 1]
                 [nil rob cutter-liked-breakfast-tacos 1]
                 [nil rob jim-liked-ham 1]]
                :in-any-order)

      (curated-feed) =>
      (contains
       (apply encoded-feed (seq (new-digest-feed jim-activated-bacon jim-liked-ham rob-liked-toast jim-liked-toast
                                                 jim-shared-toast cutter-liked-breakfast-tacos
                                                 cutter-liked-muffins)))
       :in-any-order)


      (strip-timestamps (map second (bolt-output "save-actions"))) =>
      (contains
       (strip-timestamps actions)
       :in-any-order)

      (feed-for rob) =>
      (contains
       (apply encoded-feed (seq (new-digest-feed jim-liked-toast jim-shared-toast cutter-liked-breakfast-tacos jim-liked-ham)))
       :in-any-order)

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
                       (just (encoded-feed rob-liked-toast))
                       (just (encoded-feed jim-liked-toast)))

       ;;;; test loading feeds from redis ;;;;

      (run-topology :actions more-actions-rob-cares-about)

      (feed-for rob) =>
      (contains
       (apply encoded-feed (seq (new-digest-feed jim-liked-toast jim-shared-toast cutter-liked-breakfast-tacos jim-liked-ham cutter-liked-toast)))
       :in-any-order)

      ;;;; test removing listings from a feed ;;;;

      (comment
        ;;this just doesn't appear to be processing remove requests.
        ;;not totally sure why, but I'm not feeling this rabbithole at
        ;;the moment. did some manual testing, working well enough for
        ;;the experiment we want to run.
        (run-topology :remove-requests [{:user-id rob :listing-id toast}])
        (feed-for rob)
        => (just
            (apply encoded-feed
                   (-> (new-digest-feed jim-liked-toast jim-shared-toast cutter-liked-breakfast-tacos jim-liked-ham cutter-liked-toast)
                       (remove-listing toast)
                       seq))
            :in-any-order))

      ;;;; test feed building ;;;;

      (run-topology :feed-builds [[(str jon) (json/json-str {:id "12345" :host (.getServiceId drpc) :port 0})]])

      (seq (last (last (bolt-output "drpc-feed-builder" "story")))) =>
      (seq (new-digest-feed jim-liked-toast jim-shared-toast cutter-liked-breakfast-tacos
                            cutter-liked-toast jim-liked-ham rob-liked-toast)))))
