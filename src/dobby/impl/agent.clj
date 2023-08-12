(ns dobby.impl.agent
  (:require [camel-snake-kebab.core :as csk]
            [clojure.core.async :as async]
            [clojure.tools.logging :refer [info]]
            [malli.json-schema :as json-schema]
            [medley.core :refer [find-first update-existing update-existing-in]]
            [dobby.impl.log :as log]
            [dobby.impl.gpt :as gpt]
            [dobby.impl.json :refer [json-encode]]))

(defn create-agent
  ([initial-prompt on-message]
   (let [input  (async/chan)
         output (async/chan)
         stop   (async/chan)]
     {:stop           stop
      :initial-prompt initial-prompt
      :input          input
      :output         output
      :on-message     on-message})))

(defn stop-agent!
  [agent]
  (let [stop (:stop agent)]
    (async/put! stop :stopped))
  agent)

(defn close!
  [agent]
  (let [channels [:input :output :stop]]
    (stop-agent! agent)
    (doseq [channel channels]
      (async/close! (get agent channel)))
    (when-some [log (:log agent)]
      (log/close! log))))

(defn- agent-functions
  [fns]
  (map (fn [fn]
         (if-some [json-schema (:json-schema (meta fn))]
           json-schema
           fn)) fns))

(defn- agent-params
  [params]
  (-> params
      (update-existing :functions agent-functions)))

(defn- write-output
  [agent payload]
  (async/put! (:output agent) payload)
  agent)

(defn- stream
  [context agent]
  (let [start-stream #(gpt/stream context %)]
    (-> (write-output agent {:type :begin :content nil})
        (:params)
        (agent-params)
        (start-stream))))

(defn start-agent!
  [agent log]
  (let [{:keys [stop input output on-message initial-prompt]} agent
        broadcast                                              (fn [message]
                                                                 (-> agent 
                                                                     (write-output {:type :end :content nil})
                                                                     (on-message message))
                                                                 message)]
    (async/go-loop [ctx (log/init! log initial-prompt)]
      (when-some [[message port] (async/alts! [stop input])]
        (cond
          (= port stop) (info "Agent stopped")
          :else
          (let [update-log! (partial log/append! log message)]
            (-> (conj ctx message)
                (stream agent)
                (gpt/transpose output)
                (broadcast)
                (update-existing-in [:function_call :arguments] json-encode)
                (update-log!)
                (recur))))))
    (-> agent
        (assoc :log log))))

(defn context
  [agent]
  (when-some [log (:log agent)]
    (log/context log)))

(defn send-message
  [agent message]
  (async/put! (:input agent) message))

(defn send-text
  [agent text]
  (send-message agent {:role "user" :content text}))

(defn invoke
  ([agent arguments func name]
   (let [result  (func agent arguments)
         message {:role    "function"
                  :name    name
                  :content (json-encode result)}]
     (send-message agent message)))
  ([agent arguments func]
   (invoke agent arguments func (get-in (meta func) [:json-schema :name]))))

(defn- function-matches?
  [name]
  (fn [func]
    (if-some [json-schema (:json-schema (meta func))]
      (= (:name json-schema) name)
      false)))

(defn dispatch
  [agent message]
  (when-some [function (:function_call message)]
    (some->> (get-in agent [:params :functions])
             (find-first (function-matches? (:name function)))
             (invoke agent (:arguments function))))
  agent)

(defn stream-chat
  [agent fn-1]
  (let [output (:output agent)]
    (async/go-loop []
      (when-some [event (async/<! output)]
        (fn-1 event)
        (recur)))))

(defmacro defunction
  [name description schema args & body]
  (let [schema-name (csk/->snake_case name)
        json        {:name        (str schema-name)
                     :description description
                     :parameters  (dissoc (json-schema/transform schema) :additionalProperties)}]
    `(def ~name
       (with-meta (fn ~args ~@body)
         {:json-schema ~json}))))

(defmacro defagent
  [name prompt & rest]
  (let [attr-map?  (map? (first rest))
        attr-map   (if attr-map? (first rest) nil)
        args       (vec (if attr-map? (second rest) (first rest)))
        body       (if attr-map? (drop 2 rest) (drop 1 rest))]
    ;; For this example, we simply ignore the attr-map.
    ;; But you can use it as you deem appropriate in your application.
    `(def ~name (assoc (create-agent ~prompt (fn ~args ~@body)) :params ~attr-map))))
