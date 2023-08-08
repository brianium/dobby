(ns user
  (:require [dobby.core :refer [defagent defunction dispatch add-state-watch stream-chat start-agent! send-text context stop-agent!]]))

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
  [agent message]
  (dispatch agent message))

(add-state-watch weather-assistant :log (fn [agent old new]
                                    (println (:initial-prompt agent))
                                    (println (str "State was " old))
                                    (println (str "State is now " new))))

#_(def started-weather-assistant 
    (let [started (start-agent! weather-assistant)] 
      (stream-chat weather-assistant #(println %))
      (send-text weather-assistant "What is the weather like in Boston?")
      started))

#_(send-text weather-assistant "Seems a little cold no?")

#_(context started-weather-assistant)

#_(stop-agent! started-weather-assistant)

