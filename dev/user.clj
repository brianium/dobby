(ns user
  (:require [dobby.core :refer [defagent defunction dispatch stream-chat start-agent! send-text context stop-agent! create-log close!]]))

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

(def log (create-log))

(defn handle-output
  [event]
  (let [{:keys [type content]} event]
    (case type
      :begin  (println "Beginning response")
      :end    (println "Response finished")
      (print content))))

#_(def started-weather-assistant 
    (let [started (start-agent! weather-assistant log)] 
      (stream-chat weather-assistant handle-output)
      (send-text weather-assistant "What is the weather like in Boston?")
      started))

#_(send-text weather-assistant "What clothing should I wear?")

#_(context started-weather-assistant)

#_(stop-agent! started-weather-assistant)

#_(close! started-weather-assistant)

