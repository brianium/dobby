(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [dobby.core :as agent :refer [defagent stream]]
            [juxt.clip.repl :refer [start stop reset set-init! system]]))

(repl/set-refresh-dirs "src" "dev" "resources")

(defagent Roker
  :prompt "agents/Roker.txt"
  
  (get-current-weather
   "Get the current weather in a given location"
   [:map
    [:location {:description "The city and state, e.g. San Francisco, CA"} :string]
    [:unit {:optional true} [:enum {:json-schema/type "string"} "celsius" "fahrenheit"]]]
   [_ {:keys [location unit]}]
   {:temperature 22 :unit "celsius" :description "Sunny"}))

(defn start!
  ([id log model]
   (let [started (Roker> id log model)]
     (stream started
       [event]
       (println event))))
  ([log model]
   (start! (random-uuid) log model)))

(def system-config
  {:components
   {:log   {:start `(agent/create-log)}
    :model {:start `(agent/create-model {:model "gpt-3.5-turbo"})}
    :agent {:start `(start! (clip/ref :log) (clip/ref :model))
            :stop  'agent/stop!}}})

(set-init! (constantly system-config))

(defn get-context []
  (let [agent (:agent system)]
    (agent/get-context agent)))

(defn send-message!
  [message]
  (let [agent (:agent system)]
    (agent/send-message! agent message)))

#_(reset)

#_(stop)

#_(send-message! {:role "user" :content "What is the weather like in Boston?"})

#_(get-context)
