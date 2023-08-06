(ns user
  (:require [dobby.core :refer [defagent defunction dispatch stream-chat start-agent! send-text context stop-agent!]]))

(defunction get-current-weather
  "Get the current weather in a given location"
  [:map {:closed true}
   [:location {:description "The city and state, e.g. San Francisco, CA"} :string]
   [:unit {:optional true} [:enum {:json-schema/type "string"} "celsius" "fahrenheit"]]]
  [_ {:keys [location unit]}]
  {:temperature 22 :unit "celsius" :description "Sunny"})

(defagent weather-guy
  "You are a helpful weather bot that delivers useful weather information"
  {:functions [get-current-weather]}
  [agent message]
  (dispatch agent message))

#_(def started-weather-guy 
    (let [started (start-agent! weather-guy)] 
      (stream-chat weather-guy #(println %))
      (send-text weather-guy "What is the weather like in Boston?")
      started))

#_(send-text weather-guy "Seems a little cold no?")

#_(context started-weather-guy)

#_(stop-agent! started-weather-guy)

