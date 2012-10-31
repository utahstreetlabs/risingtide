(ns risingtide.integration.storm
  (:require
   [clojure.data.json :as json]
   [risingtide.storm.core :refer [feed-generation-topology]]
   [risingtide.storm.active-user-bolt :refer [active-users-atom]]
   [risingtide.model.feed.digest :refer [new-digest-feed]]
   [risingtide.test]
   [risingtide.test.support
    [entities :refer :all]
    [stories :refer :all]
    [actions :refer :all]]
   [risingtide.integration.support :refer :all]
   [backtype.storm [testing :refer
                    [with-local-cluster with-simulated-time-local-cluster ms= complete-topology read-tuples]]]
   [midje.sweet :refer :all])
  (:import [backtype.storm LocalDRPC]))

(def drpc (LocalDRPC.))

(defn complete-feed-generation-topology [& {actions :actions feed-build-requests :feed-builds
                                            :or {actions [] feed-build-requests []}}]
  (with-local-cluster [cluster]
    (complete-topology cluster
                       (feed-generation-topology drpc)
                       :mock-sources {"actions" (map vector actions)
                                      "drpc-feed-build-requests" feed-build-requests})))

(def jim-activated-bacon (listing-activated jim bacon nil nil))
(def jim-liked-ham (listing-liked jim ham nil nil))
(def jim-liked-toast (listing-liked jim toast nil nil))
(def jim-shared-toast (listing-shared jim toast nil nil nil))
(def cutter-liked-breakfast-tacos (listing-liked cutter breakfast-tacos nil nil))

(def topology-results (atom nil))

(defn run-topology [& args]
  (swap! topology-results (constantly (apply complete-feed-generation-topology args))))

(defn bolt-output [name]
  (read-tuples @topology-results name))

(defn copious-background [& {follows :follows likes :likes active-users :active-users}]
  (let [users (distinct (concat (keys follows) (vals follows) (keys likes)))]
    (doseq [user users]
      (is-a-user user))
    (doseq [[follower followee] follows]
      (creates-user-follow follower followee))
    (doseq [[liker listing] likes]
      (creates-listing-like liker listing))
    (swap! risingtide.storm.active-user-bolt/active-users-atom (constantly active-users))))

(let [actions (on-copious
               (jim activates bacon)
               (jim likes ham)
               (jim likes toast)
               (jim shares toast)
               (cutter likes breakfast-tacos))]

  (against-background
    [(before :contents
             (copious-background
              :follows {rob cutter}
              :likes {rob toast}
              :active-users [rob])

             :after (do
                      (clear-mysql-dbs!)
                      (clear-action-solr!)))]

    (facts "about a basic topology"
      (run-topology :actions actions)

      (bolt-output "stories") =>
      [[nil nil jim-activated-bacon]
       [nil nil jim-liked-ham]
       [nil nil jim-liked-toast]
       [nil nil jim-shared-toast]
       [nil nil cutter-liked-breakfast-tacos]]

      (bolt-output "active-users") =>
      [[nil rob jim-activated-bacon]
       [nil rob jim-liked-ham]
       [nil rob jim-liked-toast]
       [nil rob jim-shared-toast]
       [nil rob cutter-liked-breakfast-tacos]]

      (bolt-output "interest-reducer") =>
      (contains [[nil rob jim-liked-toast 1]
                 [nil rob jim-shared-toast 1]
                 [nil rob cutter-liked-breakfast-tacos 1]]
                :in-any-order)

      ;; we can't know fthe order in which stories are added, but we do
      ;; know that at least one of the tuples in the feed output should
      ;; be the completed feed
      (bolt-output "add-to-feed") =>
      (contains [[nil (seq (new-digest-feed jim-liked-toast jim-shared-toast cutter-liked-breakfast-tacos))]])

      (bolt-output "curated-feed") =>
      (contains [[nil (seq (new-digest-feed jim-activated-bacon jim-liked-ham jim-liked-toast
                                            jim-shared-toast cutter-liked-breakfast-tacos))]])

      (map second (bolt-output "save-actions")) =>
      actions


      ;;;; test feed building ;;;;

      (run-topology :feed-builds [[(str rob) (json/json-str {:id "12345" :host (.getServiceId drpc) :port 0})]])

      (map last (bolt-output "drpc-actions")) =>
      (contains
       (map #(dissoc % :feed)
            (filter #(or (= (:actor_id %) cutter) (= (:listing_id %) toast))
                    actions))
       :in-any-order)

      (seq (last (last (bolt-output "drpc-feed-builder")))) =>
      (seq (new-digest-feed jim-liked-toast jim-shared-toast cutter-liked-breakfast-tacos)))))
