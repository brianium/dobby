(ns dobby.log.atom
  (:require [dobby.log.impl :refer [Log]]))

(defrecord AtomLog [*atom]
  Log
  (append!
   [_ id messages]
   (swap! *atom update id (fnil into []) messages))
  (get-context
   [_ id]
   (get @*atom id []))
  (exists?
   [_ id]
   (contains? @*atom id))
  (count
   [_ id]
   (let [context (get @*atom id [])]
     (clojure.core/count context))))

(defn create-log []
  (let [*data (atom {})]
    (AtomLog. *data)))
