# Dobby

> Dobby is free!

[![Clojars Project](https://img.shields.io/clojars/v/com.github.brianium/dobby.svg)]

## Table of contents
- [Usage](#usage)
    - [Config](#config)
- [Agents](#agents)
    - [Communicating with agents](#communicating-with-agents)
    - [Agent state](#agent-state)
- [Functions](#functions)
- [Logs](#logs)

### Usage

```clojure
(require '[dobby.core :as dobby :refer [defagent defunction]])

;;; Define an optional function for use by the assistant

(defunction get-current-weather
  "Get the current weather in a given location"
  [:map
   [:location {:description "The city and state, e.g. San Francisco, CA"} :string]
   [:unit {:optional true} [:enum {:json-schema/type "string"} "celsius" "fahrenheit"]]]
  [_ {:keys [location unit]}]
  {:temperature 22 :unit "celsius" :description "Sunny"})

;;; Define the assistant

(defagent weather-assistant
  "You are a helpful weather bot that delivers useful weather information"
  {:functions [get-current-weather]}
  [agent message]
  (dobby/dispatch agent message))

;;; Start the agent with a fresh context log

(def agent (dobby/start-agent! weather-assistant))

;;; Stream responses as they become available

(dobby/stream-chat agent #(println %))

;;; Send a simple message to the agent

(dobby/send-text agent "What is the weather like in Boston?")

;;; Check the current context of the agent

(dobby/context agent)

;;; Stop the agent

(dobby/stop-agent! agent)

```

#### Config

Dobby currently only supports completions via OpenAI's [Chat Completions API](https://platform.openai.com/docs/guides/gpt/chat-completions-api).

There are two ways to provide credentials for Dobby to use.

##### Environment variables

If the `OPEN_AI_KEY` and `OPEN_AI_ORGANIZATION` variables are set in your environment, they will be used by Dobby to communicate with the OpenAI API.

##### Agent by agent config

When defining an agent, credentials can be provided via the `attr-map` provided to the macro:

```clojure
(defagent weather-assistant
  "You are a helpful weather bot that delivers useful weather information"
  {:api-key      "your-api-key"
   :organization "your-organization"
   :functions    [get-current-weather]}
  [agent message]
  (dobby/dispatch agent message))
```

### Agents

An agent is defined using the `defagent` macro:

```clojure
(defagent weather-assistant
  "You are a helpful weather bot that delivers useful weather information"
  {:model     "gpt-4"
   :functions [a-function another-function]}
  [agent message]
  (dobby/dispatch agent message))
```

Things to note:
- The docstring of the macro is used as the agent's initial prompt
- The `attr-map` of the macro is used to override default parameters to the chat completions API
- The `:functions` key is used to provide a list of functions that the agent can use to respond to messages. You can provide this manually in a json schema format, but you probably want to use the `defunction` macro for convenience. 
- The agent body is a function body that is invoked when complete messages are received. A message will be of the format `{:role "assistant" :content "some content"}`

An agent is just a Clojure map at the end of the day. It is pretty inert until started. If you want to access different dependencies in your agent body or within functions, you can just add them before you start the agent:

```clojure
(def agent
  (-> weather-assistant
      (assoc :deps {:db (create-connection-somehow)}
      (dobby/start-agent!))))
```

Note: If you do this, take care not to override the following reserved keys:
`:initial-prompt`, `:input`, `:output`, `:on-message`, and `:state`

#### Communicating with agents

```clojure
(require '[dobby.core :refer [send-message]])

;;; Send a complete message to an agent
(send-message agent {:role "user" :content "What is the weater like in Boston?"})
```

```clojure
(require '[dobby.core :refer [send-text]])

;;; Send a text message as a user role
;;; Short hand for the above send-message call
(send-text agent "What is the weater like in Boston?")
```

#### Agent state

```clojure
(require '[dobby.core :refer [state add-state-watch]])

;;; Get the current state of an agent
(state agent) ;;; :inert, :waiting, or :responding

;;; Call a function when the state changes
(add-state-watch
  agent
  (fn [agent old new]
    (println "Agent state changed from " old " to " new)))
```

### Functions

See this [OpenAI document](https://openai.com/blog/function-calling-and-other-api-updates) for more information on functions.

The ideal way to define a function is to use the `defunction` macro:

```clojure
(defunction get-current-weather
  "Get the current weather in a given location"
  [:map {:closed true}
   [:location {:description "The city and state, e.g. San Francisco, CA"} :string]
   [:unit {:optional true} [:enum {:json-schema/type "string"} "celsius" "fahrenheit"]]]
  [agent {:keys [location unit]}]
  {:temperature 22 :unit "celsius" :description "Sunny"})
```

The macro docstring is used as the description of the function in the resulting json-schema.

After the docstring, a [malli](https://github.com/metosin/malli) map schema is provided to generate the json-schema for the function.

The rest of the macro covers the function body. All functions are passed the agent as the first argument, and the function arguments provided by GPT as the second argument.

And agent can make use of any functions by providing them in the `:functions` key of the agent's `attr-map`:

```clojure
(defagent weather-assistant
  "You are a helpful weather bot that delivers useful weather information"
  {:functions [get-current-weather]}
  [agent message]
  (dispatch agent message))
```

Functions are executed within the body of an agent by calling the `dispatch` or `invoke` functions:

```clojure
(require '[dobby.core :refer [dispatch invoke]])

;;; Dispatch will look for any matching functions and invoke them, sending
;;; the result to the agent
(dispatch agent message)

;;; Invoke will explicitly call a function and send the result
;; to the agent
(invoke agent get-current-weater {:location "Boston"} "get_current_weather")
```

`dispatch` is the easiest way to make sure an agent function is called. Many agents may only need this call in the agent body.

### Logs

The log is what tracks the context used by the agent. It backs the "chat loop". A log can be given as the second argument to `start-agent!`, but if none is given, a simple log powered by a Clojure atom will be used.

A custom log can be implemented by defining an implementation of the `dobby.impl.log/Log` protocol.
