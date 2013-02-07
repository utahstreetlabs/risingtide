(ns risingtide.integration.storm.interests-bolts
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
            :dislikes {cutter danishes
                       jim danishes}
            :blocks {cutter travis
                     jim travis}
            :tag-likes {rob breakfast
                        jon dangerous}
            :listings {cutter [ham]
                       rob [danishes]
                       jim [shark-board rocket-board veal kitten]}
            :collections {meats-i-like [veal kitten]
                          cutterz-hot-surfboards [shark-board rocket-board]}
            :collection-follows {cutter [meats-i-like]
                                 rob [cutterz-hot-surfboards]
                                 jim [cutterz-hot-surfboards]})
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
    (seller-follow-scores [rob] {:seller-id cutter})
    => {rob 1}

    (seller-follow-scores [rob jon jim] {:seller-id cutter})
    => {rob 1 jim 0 jon 1}

    (seller-follow-scores [] {:seller-id cutter})
    => {}

    (seller-follow-scores nil {:seller-id cutter})
    => {}

    (seller-follow-scores [rob] {})
    => {rob 0}
    )


  (facts "about dislike-scores"
    (dislike-scores [cutter] {:listing-id danishes})
    => {cutter -100}

    (dislike-scores [rob cutter jim] {:listing-id danishes})
    => {rob 0 cutter -100 jim -100}

    (dislike-scores [] {:listing-id danishes})
    => {}

    (dislike-scores nil {:listing-id danishes})
    => {}

    (dislike-scores [rob] {})
    => {rob 0})

  (facts "about block-scores"
    (block-scores [cutter] {:actor-id travis})
    => {cutter -100}

    (block-scores [rob cutter jim] {:actor-id travis})
    => {rob 0 cutter -100 jim -100}

    (block-scores [] {:actor-id travis})
    => {}

    (block-scores nil {:actor-id travis})
    => {}

    (block-scores [rob] {})
    => {rob 0})

  (facts "about seller-block-scores"
    (seller-block-scores [cutter] {:seller-id travis})
    => {cutter -100}

    (seller-block-scores [rob cutter jim] {:seller-id travis})
    => {rob 0 cutter -100 jim -100}

    (seller-block-scores [] {:seller-id travis})
    => {}

    (seller-block-scores nil {:seller-id travis})
    => {}

    (seller-block-scores [rob] {})
    => {rob 0})

  (facts "about collection-follow-scores"
    (collection-follow-scores [cutter jim] {:listing-id veal})
    => {cutter 1 jim 0}

    (collection-follow-scores [rob cutter jim] {:listing-id shark-board})
    => {rob 1 cutter 0 jim 1}

    (collection-follow-scores [] {:listing-id danishes})
    => {}

    (collection-follow-scores nil {:listing-id danishes})
    => {}

    (collection-follow-scores [rob] {})
    => {rob 0})
  )
