(ns dobby.impl.log)

(defprotocol Log
  (-append! [self message])
  (-context [self])
  (-init! [self initial-prompt])
  (-close! [self]))

(deftype AtomLog [*atom]
  Log
  (-append! [_ message]
    (reset! *atom (conj @*atom message)))
  (-context [_]
    @*atom)
  (-init! [_ initial-prompt]
    (let [current @*atom]
      (if (empty? current)
        (reset! *atom [{:role "system" :content initial-prompt}])
        current)))
  (-close! [_]
    (reset! *atom [])))

(defn create-atom-log
  "A simple log backed by an atom"
  []
  (AtomLog. (atom [])))

(defn context
  "Fetches the current context from the log. This is what will be used
   to stream responses from GPT"
  [log]
  (-context log))

(defn append!
  "Append one or more messages to the log. Returns the new context"
  [log & messages]
  (doseq [message messages]
    (-append! log message))
  (context log))

(defn init!
  "Initialize a log with an initial prompt. Returns the initialized context"
  [log initial-prompt]
  (-init! log initial-prompt)
  (context log))

(defn close!
  "Perform any cleanup on the log if necessary"
  [log]
  (-close! log))
