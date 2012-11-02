(ns risingtide.storm.drpc.local-server
  (require [clojure.tools.logging :as log])
  (:import [org.apache.thrift7.server THsHaServer THsHaServer$Args]
           [org.apache.thrift7.protocol TBinaryProtocol$Factory]
           [org.apache.thrift7.transport TNonblockingServerSocket]
           [backtype.storm.generated DistributedRPC$Iface DistributedRPC$Processor]))

(defn service-handler [drpc]
  (reify DistributedRPC$Iface
    (^String execute [this ^String function ^String args]
      (.execute drpc function args))))

(defn run! [drpc port]
  (let [server (THsHaServer. (-> (TNonblockingServerSocket. port)
                                 (THsHaServer$Args.)
                                 (.workerThreads 4)
                                 (.protocolFactory (TBinaryProtocol$Factory.))
                                 (.processor (DistributedRPC$Processor.
                                              (service-handler drpc)))))]
    (future (.serve server))
    (log/info "Started drpc server on " port)
    server))

