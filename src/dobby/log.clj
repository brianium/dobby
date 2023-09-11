(ns dobby.log
  (:require [dobby.log.impl :as impl]
            [dobby.log.atom :as atom])
  (:refer-clojure :exclude [count]))

(defn append!
  [log id messages]
  (impl/append! log id messages)
  log)

(defn get-context
  [log id]
  (impl/get-context log id))

(defn exists?
  [log id]
  (impl/exists? log id))

(defn count
  [log id]
  (impl/count log id))

(defn create-log []
  (atom/create-log))

(defn is-log?
  [x]
  (impl/is-log? x))
