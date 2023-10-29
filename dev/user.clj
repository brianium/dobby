(ns user
  (:require [clojure.core.async :as async :refer [<! go-loop put! close!]]
            [clojure.java.io :as io]
            [dobby.core :as dobby]))

(defn say!
  "While the dobby.core/agent expects messages to be written in a chat gpt friendly
   style, it is easy to write functions that do this translation for you"
  [agent str]
  (put! agent {:role "user" :content str}))

(defn reply!
  "Takes an assistant response and treats it as a reply to the given agent - effectively
   converting the assistant role to a user role"
  [agent response]
  (let [[type data] response]
    (when (= type :response/completed)
      (say! agent (:content data)))))

(defn dialog!
  "Prints a reply out with a label to help visualize a conversation"
  [agent label response]
  (when (reply! agent response)
    (println (format "\n%s: %s" label (:content (second response))))))

(def grumbos-fan (dobby/agent (io/resource "grumbos-fan.txt")))

(def flurbos-fan (dobby/agent (io/resource "flurbos-fan.txt")))

(go-loop []
  (when-some [response (<! grumbos-fan)] 
    (dialog! flurbos-fan "Grumbos Fan" response)
    (recur)))

(go-loop []
  (when-some [response (<! flurbos-fan)]
    (dialog! grumbos-fan "Flurbos Fan" response)
    (recur)))

#_(say! grumbos-fan "Well, well, welll... If it isn't a no good Grumbos fan!")

#_(->> (filter #(= (:role %) "assistant") grumbos-fan)
       (map :content))

#_(->> (filter #(= (:role %) "assistant") flurbos-fan)
       (map :content))

#_(doseq [ag [grumbos-fan flurbos-fan]]
    (close! ag))
