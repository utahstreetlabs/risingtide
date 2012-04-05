(ns risingtide.config)

(def redis
  {:development {:resque {} :card-feeds {} :network-feeds {} :interests {} :stories {}}
   :staging {:resque {:host "staging3.copious.com"}
             :card-feeds {:host "staging4.copious.com"}
             :network-feeds {:host "staging4.copious.com"}
             :interests {:host "staging4.copious.com"}
             :stories {:host "staging4.copious.com"}}
   :demo {:resque {:host "demo1.copious.com"}
          :card-feeds {:host "demo1.copious.com"}
          :network-feeds {:host "demo1.copious.com"}
          :interests {:host "demo1.copious.com"}
          :stories {:host "demo1.copious.com"}}
   :production {:resque {:host "resque-redis-master.copious.com"}
                :card-feeds {:host "mag-redis-master.copious.com"}
                :network-feeds {:host "mag-redis-master.copious.com"}
                :interests {:host "mag-redis-master.copious.com"}
                :stories {:host "mag-redis-master.copious.com"}}})

(def digest
  {:development true
   :staging true
   :production true})

(def max-card-feed-size 1000)
(def max-network-feed-size 250)

(def ports {:admin 4050
            :mycroft 4055})

