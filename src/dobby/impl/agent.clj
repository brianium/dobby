(ns dobby.impl.agent
  (:require [clojure.core.async :as async :refer [<! go-loop]]
            [clojure.core.async.impl.protocols :as async.proto]
            [dobby.impl.bidi :as bidi]
            [dobby.impl.gpt :as gpt])
  (:refer-clojure :exclude [agent]))

(deftype Agent [bidi log]
  async.proto/ReadPort
  (take! [_ handler]
    (async.proto/take! bidi handler))
  
  async.proto/WritePort
  (put! [_ val handler] 
    (async.proto/put! bidi val handler))
  
  async.proto/Channel
  (close! [_]
    (async.proto/close! bidi))
  
  clojure.lang.Seqable
  (seq [_]
    (seq @log)))

(defn- put-and-close!
  [val ch]
  (async/put! ch val)
  (async/close! ch))

(defn create-agent
  "Creates an agent. An agent is a bidirectional channel that can be used to send and receive messages
   from gpt. It functions as a core.async channel that can be read from and written to. It maintains a stateful
   log"
  [log & {:keys [model] :as opts :or {model "gpt-3.5-turbo"}}]
  (let [*log         (atom log)
        input        (async/chan)
        output       (async/chan)

        mult         (async/mult output)
        read-stream  (async/chan) ;;; A channel for reading output externally
        write-stream (async/chan) ;;; A channel for reading internally for updating the log
        
        agent        (Agent. (bidi/bidi-chan input read-stream) *log)]
     ;;; All output goes to the read and write streams
    (async/tap mult read-stream)
    (async/tap mult write-stream)

     ;;; Handles input sent to agent - responses are piped to the mult'ed output channel
    (go-loop []
      (when-some [msg (<! input)]
        (let [gpt-ch (gpt/stream (merge opts {:model    model
                                              :messages (swap! *log conj msg)}))]
          (async/pipeline-async 1 output put-and-close! gpt-ch))
        (recur)))
    
     ;;; Reads exclusively from the write-stream in order to update the log with response data
    (go-loop []
      (when-some [response (<! write-stream)]
        (let [[id body] response]
          (when (= id :response/completed)
            (swap! *log conj body)))
        (recur)))
    
     ;;; Return the agent
    agent))

(defmulti create
  (fn [x & args]
    (type x)))

(defmethod create java.lang.String
  [str & {:as opts}]
  (create-agent [{:role "system" :content str}] opts))

(defmethod create clojure.lang.PersistentVector
  [m & {:as opts}]
  (create-agent m opts))

(defmethod create clojure.lang.PersistentArrayMap
  [m & {:as opts}]
  (create [m] opts))

(defmethod create java.net.URL
  [u & {:as opts}]
  (create (slurp u) opts))

(defmethod create :default
  [x {:as opts}]
  (let [t (type x)]
    (throw (ex-info (format "Cannot create agent from %s" t) {:args [x] :opts opts}))))
