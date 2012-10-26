(ns risingtide.feed)

(defprotocol Feed
  (add [feed story] "")
  (min-timestamp [feed])
  (max-timestamp [feed]))
