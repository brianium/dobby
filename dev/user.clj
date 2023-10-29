(ns user
  (:require [clojure.core.async :as async :refer [<! go-loop put! close!]]
            [clojure.java.io :as io]
            [dobby.core :as dobby]))

(def c (dobby/agent (io/resource "prompt.txt")))

(go-loop []
  (when-some [msg (<! c)]
    (println msg)
    (recur)))

#_(put! c {:role "user" :content "What is your name?"})

#_(map :content c)

#_(close! c)
