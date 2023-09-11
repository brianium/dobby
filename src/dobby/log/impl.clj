(ns dobby.log.impl
  (:refer-clojure :exclude [count]))

(defprotocol Log
  (append! [_ id messages] "Append 1 or more messages to the log")
  (get-context [_ id] "Return the context to be used for the given id")
  (exists? [_ id] "Check if context already exists for the given id")
  (count [_ id] "Return the number of messages in the log for the given id"))

(defn is-log?
  [x]
  (satisfies? Log x))
