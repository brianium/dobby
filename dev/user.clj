(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [dobby.core :as agent :refer [defagent defunction stream]]
            [juxt.clip.repl :refer [start stop reset set-init! system]]))

(repl/set-refresh-dirs "src" "dev")

(defunction get-current-weather
  "Get the current weather in a given location"
  [:map {:closed true}
   [:location {:description "The city and state, e.g. San Francisco, CA"} :string]
   [:unit {:optional true} [:enum {:json-schema/type "string"} "celsius" "fahrenheit"]]]
  [_ {:keys [location unit]}]
  {:temperature 22 :unit "celsius" :description "Sunny"})

(defagent weather-assistant
  "You are a helpful weather bot that delivers useful weather information"
  {:functions [get-current-weather]}
  [agent]
  (println (agent/context-length agent))
  (agent/append!
   agent
   {:role "user" :content "I want you to preface every response with the exclamation 'Hot Dog!'"}))

(defn start!
  ([id log model]
   (let [started (weather-assistant id log model)]
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

