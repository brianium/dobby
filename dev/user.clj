(ns user
  (:require [clojure.core.async :as async :refer [<! go-loop put! close!]]
            [clojure.java.io :as io]
            [dobby.core :as dobby]))

(defn say!
  "While the dobby.core/agent expects messages to be written in a chat gpt friendly
   style, it is easy to write functions that do this translation for you"
  [agent str]
  (put! agent {:role "user" :content str}))

(def swordsman (dobby/agent (io/resource "swordsman.txt")))

;;; Let's read from the swordsman as output becomes available
(go-loop []
  (when-some [msg (<! swordsman)]
    (println msg)
    (recur)))

#_(say! swordsman "What is your name?")

#_(map :content swordsman)

#_(close! swordsman)
