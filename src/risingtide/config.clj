(ns risingtide.config)

(def redis
  {:development {:resque {} :feeds {}}
   :staging {:resque {:host "staging3.copious.com"}
             :feeds {:host "staging4.copious.com"}}
   :demo {:resque {:host "demo1.copious.com"}
          :feeds {:host "demo1.copious.com"}}

   :production {:resque {:host "resque-redis-master.copious.com"}
                :feeds {:host "mag-redis-master.copious.com"}}})

(def local-log
  ["risingtide"
   {:level :trace :out
    (org.apache.log4j.DailyRollingFileAppender.
     (org.apache.log4j.EnhancedPatternLayout. org.apache.log4j.EnhancedPatternLayout/TTCC_CONVERSION_PATTERN)
     "logs/risingtide.log" ".yyyy-MM-dd")}])

(def syslog
  ["risingtide"
   {:level :debug :out
    (org.apache.log4j.net.SyslogAppender.
     (org.apache.log4j.EnhancedPatternLayout. org.apache.log4j.EnhancedPatternLayout/TTCC_CONVERSION_PATTERN)
     "localhost"
     org.apache.log4j.net.SyslogAppender/LOG_USER)}])

(def loggers
  {:development local-log
   :staging syslog
   :production syslog})

(def digest
  {:development true
   :staging false
   :production false})