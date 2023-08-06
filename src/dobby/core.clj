(ns dobby.core
  (:require [dobby.impl.agent :as agent]))

(defn add-state-watch
  "Add a function that gets called whenever the agent's internal state
   changes. The given function will be called with the agent, old value and new value.
   Possible values are:
   :inert - The agent is not doing anything
   :waiting - The agent is waiting to respond
   :responding - The agent is currently responding to a message"
  [agent id fn-3]
  (agent/add-state-watch agent id fn-3))

(defn context
  "Get the agent's current context. An agent's log can not be accessed
   until it is started"
  [agent]
  (agent/context agent))

(defn create-agent
  "Create a new agent. The on-message function is called with the agent and a complete message
   after a stream is fully consumed. An optional dependencies map can be provided so dependencies
   can be accessed when invoking functions"
  ([initial-prompt on-message dependencies]
   (agent/create-agent initial-prompt on-message dependencies))
  ([initial-prompt on-message]
   (agent/create-agent initial-prompt on-message)))

(defn dispatch
  "Invoke a function if the message indicates one should be called. Only supports
   functions defined with defunction"
  [agent message]
  (agent/dispatch agent message))

(defn invoke
  "Invoke a function and send the result to the agent as a message. If not calling
   a function defined by defunction, you must provide a name. Functions are invoked
   with the agent and the arguments given"
  ([agent args func name]
   (agent/invoke agent args func name))
  ([agent args func]
   (agent/invoke agent args func)))

(defn remove-state-watch
  "Remove a state watch identified by the given id from an agent"
  [agent id]
  (agent/remove-state-watch agent id))

(defn send-message
  "Send a message to an agent"
  [agent message]
  (agent/send-message agent message))

(defn send-text
  "Sent a text message to an agent. Will send it via the user role"
  [agent text]
  (agent/send-text agent text))

(defn start-agent!
  "Start an agent. The given log will be used to store context. Give a log
   with pre-seeded data to start the agent with prior knowledge."
  ([agent log]
   (agent/start-agent! agent log))
  ([agent]
   (agent/start-agent! agent)))

(defn state
  "Get the current state of the agent"
  [agent]
  (agent/state agent))

(defn stream-chat
  "Stream chat text from an agent. Calls the given function everytime a chunk of
   non function output is received. The function will be called with chunks of text as they become available.
   Useful for realtime responses"
  [agent fn-1]
  (agent/stream-chat agent fn-1))

(defn stop-agent!
  "Stop an agent. Closes input and output channels"
  [agent]
  (agent/stop-agent! agent))

(defmacro defagent
  "Create an agent. This macro is structed just like clojure.core/defn. The differences
   are that the doc string will be used as the initial prompt and if an attrs-map is given,
   it will be used to provide additional parameters to GPT. Passing a function defined by defunction will ensure that the malli schema is used to generate
   the appropriate schema for GPT"
  [& args]
  `(dobby.impl.agent/defagent ~@args))

(defmacro defunction
  "Define a function that can be invoked by an agent. Any function with a json-schema
   structure defined as meta data can be used by an agent. These functions should have an
   argument signature of [agent args]"
  [& args]
  `(dobby.impl.agent/defunction ~@args))
