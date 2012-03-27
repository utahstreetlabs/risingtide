(ns risingtide.config)

(def redis
  {:development {:resque {} :feeds {}}
   :staging {:resque {:host "staging3.copious.com"}
             :feeds {:host "staging4.copious.com"}}
   :demo {:resque {:host "demo1.copious.com"}
          :feeds {:host "demo1.copious.com"}}
   :production {:resque {:host "resque-redis-master.copious.com"}
                :feeds {:host "mag-redis-master.copious.com"}}})

(def digest
  {:development true
   :staging false
   :production false})