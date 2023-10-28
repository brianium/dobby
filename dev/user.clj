(ns user
  (:require [clojure.core.async :as async :refer [<! go-loop]]
            [clojure.tools.namespace.repl :as repl]
            [dobby.impl.bidi :as bidi]
            [dobby.gpt :as gpt]
            [juxt.clip.repl :refer [start stop reset set-init! system]])
  (:refer-clojure :exclude [agent]))

(repl/set-refresh-dirs "src" "dev")

#_(def system-config
  {:components
   {:log   {:start `(agent/create-log)}
    :model {:start `(agent/create-model {:model "gpt-3.5-turbo"})}
    :agent {:start `(start! (clip/ref :log) (clip/ref :model))
            :stop  'agent/stop!}}})

#_(set-init! (constantly system-config))

#_(reset)

#_(stop)

#_(gpt/stream {:model    "gpt-3.5-turbo"
               :messages [{:role    "user"
                           :content "Hello, world!"}]})

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

(def c (agent [{:role "system" :content "You are an acient Sicilian from the 8th century B.C"}] (async/chan) (async/chan)))

(go-loop []
  (when-some [msg (<! c)]
    (println msg)
    (recur)))

#_(put! c {:role "user" :content "Tell me a little about yourself"})

#_@(:log c)

#_(close! c)
