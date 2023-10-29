(ns user
  (:require [clojure.core.async :as async :refer [<! go-loop]]
            [clojure.tools.namespace.repl :as repl]
            [dobby.impl.agent :as agent]
            [juxt.clip.repl :refer [start stop reset set-init! system]]))

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

(def log [{:role "system" :content "You are an ancient Sicilian from the 8th century B.C"}])

(def c (agent/create-agent log))

(go-loop []
  (when-some [msg (<! c)]
    (println msg)
    (recur)))

#_(put! c {:role "user" :content "What is your primary diet like?"})

#_(map :content c)

#_(close! c)
