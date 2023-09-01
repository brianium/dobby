(ns dobby.log.impl)

(defprotocol Log
  (append! [_ id messages] "Append 1 or more messages to the log")
  (get-context [_ id] "Return the context to be used for the given id")
  (exists? [_ id] "Check if context already exists for the given id"))

(defn is-log?
  [x]
  (satisfies? Log x))
