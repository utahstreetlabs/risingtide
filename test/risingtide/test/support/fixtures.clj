(ns risingtide.test.support.fixtures
  (:require
   [copious.domain.testing.entities :refer :all]
   [risingtide.test.support.stories :refer :all]))

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
