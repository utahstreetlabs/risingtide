(ns risingtide.integration.storm.interests_bolts
  (:require
   [risingtide.test]
   [risingtide.test.support
    [entities :refer :all]
    [stories :refer :all]]
   [risingtide.integration.support :refer :all]
   [midje.sweet :refer :all]

   [risingtide.storm.interests-bolts :refer :all]))

(against-background
  [(before :contents
           (copious-background
            :follows {rob cutter
                      jon cutter}
            :likes {rob toast
                    jon toast}
            :tag-likes {rob breakfast
                        jon dangerous}
            :listings {cutter ham})
           :after (do
                    (clear-mysql-dbs!)
                    (clear-action-solr!)
                    (clear-redis!)))]
  (facts "about tag-like-scores"
    (tag-like-scores [rob] {:tag-ids [breakfast lunch]})
    => {rob 1}

    (tag-like-scores [rob jon] {:tag-ids [breakfast lunch]})
    => {rob 1
        jon 0}

    (tag-like-scores [rob jon] {:tag-ids [breakfast lunch dangerous]})
    => {rob 1
        jon 1}

    (tag-like-scores [rob] {})
    => {rob 0}

    (tag-like-scores [rob] {:tag-ids []})
    => {rob 0}

    (tag-like-scores nil {:tag-ids [breakfast]})
    => {}

    (tag-like-scores [] {:tag-ids [breakfast]})
    => {}
    )


  (facts "about like-scores"
    (like-scores [rob] {:listing-id toast})
    => {rob 1}

    (like-scores [rob jon jim] {:listing-id toast})
    => {rob 1 jim 0 jon 1}

    (like-scores [] {:listing-id toast})
    => {}

    (like-scores nil {:listing-id toast})
    => {}

    (like-scores [rob] {})
    => {rob 0})

  (facts "about follow-scores"
    (follow-scores [rob] {:actor-id cutter})
    => {rob 1}

    (follow-scores [rob jon jim] {:actor-id cutter})
    => {rob 1 jim 0 jon 1}

    (follow-scores [] {:actor-id cutter})
    => {}

    (follow-scores nil {:actor-id cutter})
    => {}

    (follow-scores [rob] {})
    => {rob 0}
    )

  (facts "about seller-follow-scores"
    (seller-follow-scores [rob] {:listing-id ham})
    => {rob 1}

    (seller-follow-scores [rob jon jim] {:listing-id ham})
    => {rob 1 jim 0 jon 1}

    (seller-follow-scores [] {:listing-id ham})
    => {}

    (seller-follow-scores nil {:listing-id ham})
    => {}

    (seller-follow-scores [rob] {})
    => {rob 0}
    )
)
