(ns dobby.agent
  (:require [camel-snake-kebab.core :as csk]
            [cheshire.core :as json]
            [clojure.core.async :as async]
            [clojure.tools.logging :refer [errorf]]
            [dobby.log :as log]
            [dobby.model :as model]
            [malli.json-schema :as json-schema]
            [medley.core :as m]))

(defn define-agent
  "Defines the template from which a started agent is created"
  [initial-prompt init functions]
  {:init           init
   :initial-prompt initial-prompt
   :functions      functions})

(defn- init-log!
  "Initialize the log with the agent's initial prompt"
  [agent]
  (let [{:keys [initial-prompt id]} agent]
    (update agent :log log/append! id [{:role "system" :content initial-prompt}])))

(defn- initialize!
  "Initialize the agent with an ID and a log.
   
   The log will only be initilized if the given log implementation
   returns false for (log/exists? log id)

   @todo - Currently the log is initialized with a value that is only meaningful to OpenAI's API. This could
   be abstracted to the model perhaps so a model implementation is in charge of converting some intermediary format
   to something sensible to itself"
  [agent]
  (let [{:keys [id log init]} agent]
    (cond-> agent
      (not (log/exists? log id)) (init-log!)
      :always                    (-> (init) 
                                     (update :model model/initialize agent)))))

(defn- is-error?
  "Check if the given message is an error"
  [message]
  (contains? message :error))

(defn- is-function?
  "Check if the mesage from the model represents a function call"
  [agent message]
  (let [{:keys [functions]} agent]
    (contains? functions (get-in message [:function_call :name]))))

(defn append!
  "Append a message to the agent's context log."
  [started-agent message]
  (let [{:keys [log id]} started-agent
        msg (m/update-existing-in message [:function_call :arguments] json/encode)] 
    (log/append! log id [msg])
    msg))

(defn context-length
  [started-agent]
  (let [{:keys [id log]} started-agent]
    (log/count log id)))

(defn send-message!
  [started-agent message]
  (let [{:keys [model]} started-agent]
    (model/send-message! model (append! started-agent message)) 
    started-agent))

(defn- invoke!
  "@todo - similar to init-context!, this is currently only meaningful to OpenAI's API. This could
   be abstracted to the model perhaps so a model implementation is in charge of converting some intermediary format
   to something sensible to itself"
  [started-agent message]
  (let [{:keys [functions]} started-agent
        name           (get-in message [:function_call :name])
        args           (json/decode
                        (get-in message [:function_call :arguments]) keyword)
        func           (get functions name)
        result         (func started-agent args)
        result-message {:role    "function"
                        :name    name
                        :content (json/encode result)}]
    (send-message! started-agent result-message)
    [result-message]))

(defn- listen!
  "Listen for messages from the model. Responses that don't map to a function will be passed
   to the message handler. Will write all responses from the model to the context log as they are
   received."
  [started-agent]
  (let [{:keys [id] {:keys [output]} :model} started-agent]
    (async/go-loop []
      (when-some [message (async/<! output)]
        (cond
          (is-error? message)
          (errorf "Agent ID: %s: Error: %s" id message) ;;; For now just log errors until an opportunity to use them arises
          
          (is-function? started-agent message)
          (invoke! started-agent (append! started-agent message))
          
          :else
          (append! started-agent message))
        (recur)))
    started-agent))

(defn get-context
  [started-agent]
  (let [{:keys [id log]} started-agent]
    (log/get-context log id)))

(defn- start-model!
  [agent]
  (let [response-ch (async/chan)]
    (-> agent
        (assoc :response-ch response-ch)
        (update :model model/start! (partial get-context agent) #(async/put! response-ch %)))))

(defn start!
  "Initialize agents"
  ([agent id log model]
   (-> (assoc agent :id id :log log :model model)
       (initialize!)
       (start-model!)
       (listen!)))
  ([agent log model]
   (start! agent (random-uuid) log model)))

(defn stop!
  [started-agent]
  (model/stop! (:model started-agent))
  (async/close! (:response-ch started-agent))
  :stopped)

(defn define-function
  [schema-name description schema fn-2]
  (let [json        {:name        schema-name
                     :description description
                     :parameters  (dissoc (json-schema/transform schema) :additionalProperties)}]
    (with-meta fn-2
      {:json-schema json})))

(defmacro defunction
  [name description schema args & body]
  (let [schema-name (str (csk/->snake_case name))]
    `(def ~name
       (define-function ~schema-name ~description ~schema (fn ~args ~@body)))))

(defn create-function-map
  [functions]
  (into {} (map (juxt #(get-in (meta %) [:json-schema :name]) identity) functions)))

(defmacro defagent
  [name prompt & rest]
  (let [attr-map?  (map? (first rest))
        attr-map   (if attr-map? (first rest) nil)
        args       (vec (if attr-map? (second rest) (first rest)))
        body       (if attr-map? (drop 2 rest) (drop 1 rest))]
    `(defn ~name
       ([id# log# model#]
        (start!
         (define-agent ~prompt (fn ~args ~@body)
           (create-function-map
            (get ~attr-map :functions [])))
         id# log# model#))
       ([log# model#]
        (~name (random-uuid) log# model#)))))

(defmacro stream
  [started-agent bindings & body]
  `(do (clojure.core.async/go-loop []
         (let [response# (clojure.core.async/<! (:response-ch ~started-agent))
               ~bindings [response#]]
           (when (some? response#)
             ~@body
             (recur))))
       ~started-agent))
