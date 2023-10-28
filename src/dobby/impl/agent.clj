(ns dobby.impl.agent
  (:require [clojure.core.async :as async :refer [<! go-loop]]
            [dobby.impl.bidi :as bidi]
            [dobby.impl.gpt :as gpt])
  (:refer-clojure :exclude [agent]))

(defn agent
  ([log input output]
   (let [*log         (atom log)
         size         (count (.buf output))

         mult         (async/mult output)
         read-stream  (async/chan (when (pos? size) size)) ;;; A channel for reading output externally
         write-stream (async/chan (when (pos? size) size)) ;;; A channel for reading internally for updating the log

         bidi         (bidi/bidi-chan input read-stream)]
     (async/tap mult read-stream)
     (async/tap mult write-stream)
     (go-loop []
       (when-some [msg (<! input)]
         (let [log    (swap! *log conj msg)
               stream (gpt/stream {:model    "gpt-3.5-turbo"
                                   :messages log})]
           (async/pipeline-async 1 output (fn [val ch]
                                            (async/put! ch val)
                                            (async/close! ch)) stream)
           (recur))))
     (go-loop []
       (when-some [response (<! write-stream)]
         (let [[id body] response]
           (when (= id :response/completed)
             (swap! *log conj body)))
         (recur)))
     (assoc bidi :log *log)))
  ([log input]
   (agent log input (async/chan)))
  ([log]
   (agent log (async/chan) (async/chan))))
