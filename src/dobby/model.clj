(ns dobby.model
  (:require [dobby.model.impl :as impl]
            [dobby.model.gpt :as gpt]))

(defn initialize
  [model agent]
  (impl/initialize model agent))

(defn send-message!
  [model message]
  (impl/send-message! model message)
  model)

(defn start!
  [model get-context handler]
  (impl/start! model get-context handler)
  model)

(defn stop! 
  [model]
  (impl/stop! model))

(defn is-model? 
  [x]
  (impl/is-model? x))

(defn create-model
  ([params type]
   (case type
     :gpt (gpt/create-model params)
     (throw (Exception. (str "Unknown model type: " type)))))
  ([params]
   (create-model params :gpt)))
