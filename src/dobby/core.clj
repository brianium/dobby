(ns dobby.core
  (:require [dobby.agent :as agent]
            [dobby.log :as log]
            [dobby.model :as model]))

(defn append!
  "Appends a message to the agent's context log. This will not interact with the
   model, but instead will be included the next time the model requires context.
   
   Useful for adding log entries to affect model responses without generating an immediate
   response.
   
   Returns the agent"
  [agent & messages]
  (doseq [msg messages]
    (agent/append! agent msg))
  agent)

(defn context-length
  "Get the number of messages currently contained in the agent's context log"
  [agent]
  (agent/context-length agent))

(defn create-log
  "Returns a new log backed by an atom. For more complex scenarios, create
   an implementation of the dobby.impl.log/Log protocol"
  []
  (log/create-log))

(defn create-model
  "Create a model backed by OpenAI's API. Params are essentially anthing supported
   by the OpenAI API. See https://platform.openai.com/docs/guides/gpt"
  [params]
  (model/create-model params))

(defn define-agent
  "Defines a prototype for starting agents with. This function is the backing function
   for the defagent macro. It is provided as an alternative to defagent, and is useful
   for scenarios where you want to create an agent without defining a new symbol."
  [initial-prompt on-message functions]
  (agent/define-agent initial-prompt on-message functions))

(defn define-function
  "This function is the backing function for the defunction macro. It is provided as an
   alternative to defunction, and is useful for scenarios where you want to create a function
   without defining a new symbol."
  [schema-name description schema fn-2]
  (agent/define-function schema-name description schema fn-2))

(defn get-context
  "Return the current context of the agent"
  [started-agent]
  (agent/get-context started-agent))

(defn send-message!
  "Send a message to the agent. For now the message format should mirro what the OpenAI
   API expects. See https://platform.openai.com/docs/api-reference/chat/create"
  [started-agent message]
  (agent/send-message! started-agent message))

(defn start!
  "Start the agent with the given id, log, and model. If an id is omitted, a random
   uuid will be used. See dobby.agent.schema to understand the difference between an agent
   and started-agent. At a high level, agent types act as a prototype for started-agents. Most
   dobby backed programs will interact with a started-agent."
  ([agent log model]
   (agent/start! agent log model))
  ([agent id log model]
   (agent/start! agent id log model)))

(defn stop!
  "Stop a started agent. This will stop the agent's model and close the response channel
   so no further responses can be streamed"
  [started-agent]
  (agent/stop! started-agent))

(defmacro defagent
  "Defines an agent constructor. Functions defined via defunction can
   be passed to the agents attribute map. The docstring for the agent will be used as the
   initial-prompt for the model.

   An agent is started by calling the constructor function following the same semantics as
   dobby.core/start! - the only difference being that the agent argument is omitted.
   
   ```clojure
   (defagent weather-assistant
     \"You are a helpful weather bot that delivers useful weather information\"
     {:functions [get-current-weather]}
     [agent message]
     (println message))
   
   (weather-assistant (random-uuid) (create-log) (create-model {:model \"gpt-3.5-turbo\"}))
   ```"
  [& args]
  `(dobby.agent/defagent ~@args))

(defmacro defunction
  "Defines a function for use with a model. Very similar to a regular Clojure function
   except it forces a schema definition via the malli library. This schema is used to communicate
   the function signature to the model by converting it to json schema. The docstring is used as the json schema description.
   The function arguments will always be [started-agent arguments]
   
   ```clojure
   (defunction get-current-weather
     \"Get the current weather in a given location\"
     [:map {:closed true}
       [:location {:description \"The city and state, e.g. San Francisco, CA\"} :string]
       [:unit {:optional true} [:enum {:json-schema/type \"string\"} \"celsius\" \"fahrenheit\"]]]
     [_ {:keys [location unit]}]
     (make-weather-api-call location unit))
   ```"
  [& args]
  `(dobby.agent/defunction ~@args))

(defmacro stream
  "Opens a stream to the given agents response stream. Responses are received as they become
   available. It is important to know that this stream will only receive events for text responses
   from the agent. Function calls are handled automatically.
   
   Events are maps of the form {:type <type> :content <content>}

   Types are as follows:
   - :begin - Represents that a response has started. :content will be nil
   - :text - Represents that text is available, :content will be a string containing the portion of the response
   - :end - Represnts that the response has ended. :content will be nil

   Streaming stops as soon as the agent is stopped.  

   ```clojure
   (stream started
     [event]
     (println event))
   ```"
  [& args]
  `(dobby.agent/stream ~@args))
