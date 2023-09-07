(ns dobby.agent
  (:require [cheshire.core :as json]
            [clojure.core.async :as async]
            [camel-snake-kebab.core :as csk]
            [dobby.log :as log]
            [dobby.model :as model]
            [malli.json-schema :as json-schema]
            [medley.core :as m]))

(defn define-agent
  "Defines the template from which a started agent is created"
  [initial-prompt on-message functions]
  {:on-message     on-message
   :initial-prompt initial-prompt
   :functions      functions})

(defn- init-context!
  "Initialize the context for an agent and return the agent
   
   @todo - Currently the log is initialized with a value that is only meaningful to OpenAI's API. This could
   be abstracted to the model perhaps so a model implementation is in charge of converting some intermediary format
   to something sensible to itself"
  [agent id log]
  (let [{:keys [initial-prompt]} agent]
    (when-not (log/exists? log id)
      (log/append! log id [{:role "system" :content initial-prompt}]))
    agent))

(defn- is-error?
  "Check if the given message is an error"
  [message]
  (contains? message :error))

(defn- is-function?
  [agent message]
  (let [{:keys [functions]} agent]
    (contains? functions (get-in message [:function_call :name]))))

(defn- append!
  "Append a message to the agent's context log."
  [started-agent message]
  (let [{:keys [log id]} started-agent
        msg (m/update-existing-in message [:function_call :arguments] json/encode)] 
    (log/append! log id [msg])
    msg))

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
  (let [{:keys [on-message] {:keys [output]} :model} started-agent]
    (async/go-loop []
      (when-some [message (async/<! output)]
        (cond
          (is-error? message)
          (on-message started-agent message)
          
          (is-function? started-agent message)
          (invoke! started-agent (append! started-agent message))
          
          :else
          (on-message started-agent (append! started-agent message)))
        (recur)))
    started-agent))

(defn start!
  "@todo - model needs to have functions added to params before starting"
  ([agent id log model]
   (let [response-ch        (async/chan)
         get-context        (partial log/get-context log id)
         started-model      (-> model
                                (model/initialize agent)
                                (model/start! get-context #(async/put! response-ch %)))]
     (-> (init-context! agent id log)
         (assoc :id id :log log :model started-model :response-ch response-ch)
         (listen!))))
  ([agent log model]
   (start! agent (random-uuid) log model)))

(defn get-context
  [started-agent]
  (let [{:keys [id log]} started-agent]
    (log/get-context log id)))

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
