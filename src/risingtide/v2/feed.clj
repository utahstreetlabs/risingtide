(ns risingtide.v2.feed)

(defprotocol Feed
  (add [feed story] "")
  (min-timestamp [feed])
  (max-timestamp [feed]))
