(ns dobby.model.gpt
  (:require [cheshire.core :as json] 
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [dobby.model.impl :refer [Model]]
            [org.httpkit.client :as http]))

(def *default-credentials (delay {:api-key      (System/getenv "OPEN_AI_KEY")
                                  :organization (System/getenv "OPEN_AI_ORGANIZATION")}))

(defn get-credentials
  "Supports getting credentials from the given params or from the environment.
   
   If credentials are not found in the params, then the environment is checked for the values
   OPEN_AI_KEY and OPEN_AI_ORGANIZATION"
  [params]
  (if (every? #(string? (get params %)) [:api-key :organization])
    (select-keys params [:api-key :organization])
    @*default-credentials))

(defn create-request
  "Create a request object that can be used to stream responses from the OpenAI API"
  [credentials data]
  (let [{:keys [api-key organization]} credentials]
    {:headers {"Authorization"       (str "Bearer " api-key)
               "Content-Type"        "application/json"
               "OpenAI-Organization" organization}
     :body    (json/encode (merge data {:stream true}))
     :url     "https://api.openai.com/v1/chat/completions"
     :method  :post
     :as      :stream}))

(defn parse-line
  "Parse a line of the response body, returning a tuple containing a result type
   and the parsed data. The result type is one of :done, :data, or :error. The data
   value will either be nil for :done, a map for :data, or the raw line for :error"
  [line]
  (cond
    (= line "data: [DONE]")
    [:done nil]

    (.startsWith line "data: ")
    (let [data (-> line
                   (subs 6) ;; trim "data: " prefix
                   (json/decode keyword))]
      [:data data])

    :else
    [:error line]))

(defn parse-body
  "Parse the response body line by line, returning a lazy sequence of parsed lines
   as results containing a type and associated data (see parse-line)"
  [bytes]
  (let [rdr (io/reader bytes :encoding "UTF-8")]
    (->> (line-seq rdr)
         (filter not-empty)
         (map parse-line))))

(defmulti apply-delta
  "Determines how to apply a delta to a message that will be sent to the model's output
   channel. Should handle content messages as well as function call messages"
  (fn [_ delta]
    (-> delta
        keys
        first)))

(defmethod apply-delta :content [message delta]
  (update message :content str (:content delta)))

(defmethod apply-delta :function_call [message delta]
  (update-in message [:function_call :arguments] str (get-in delta [:function_call :arguments])))

(defmethod apply-delta :default [message delta]
  (merge message delta))

(defn parse-message
  "If a message has function call arguments, this will convert them
   into a Clojure map - otherwise the message is returned as is"
  [message]
  (let [decode #(json/decode % keyword)]
    (if (:function_call message)
      (update-in message [:function_call :arguments] decode)
      message)))

(defn parse-error
  "Parse an error into a Clojure map"
  [error-string]
  (json/decode error-string keyword));

(defn stream-response
  "Stream a response from GPT. When a message or error is completed, it will
   be sent to the given output channel. The given handler function will be called
   with events of the form {:type <type> :content <content>}
   
   Types are as follows:
   - :begin - Represents that a response has started. :content will be nil
   - :text - Represents that text is available, :content will be a string containing the portion of the response
   - :end - Represnts that the response has ended. :content will be nil"
  [request output handler]
  (http/request
   request
   (fn [{:keys [body]}]
     (loop [chunks      (parse-body body)
            message     nil
            error       nil
            begun?      false]
       (let [[type data] (first chunks)
             choice      (some-> data :choices first)
             content?    (get-in choice [:delta :content])]
         (when (and (not begun?) content?)
           (handler {:type :begin :content nil}))
         (case type
           :done  (recur nil message error begun?)
           :error (recur (rest chunks) nil (str error data) begun?)
           :data  (let [delta (:delta choice)]
                    (when-some [content (:content delta)]
                      (handler {:type :text :content content}))
                    (recur (rest chunks) (apply-delta message delta) nil (or begun? content?)))
           (do
             (cond
               (some? message) (try
                                 (async/put! output (parse-message message))
                                 (catch Exception e
                                   (async/put! output {:error {:message (.getMessage e) :response message}})))
               (some? error)   (async/put! output (parse-error error)))
             (when begun?
               (handler {:type :end :content nil})))))))))

(defn start*
  "Start listening for messages on the model's input channel. When a message is received,
   it will be sent to the OpenAI API and the response will be streamed to the output channel. Any
   messages received on the close-ch will stop the model and close all channels. The get-context
   function is responsible for returning a vector of messages to append new messages to. This is
   what establishes the context of the conversation.
   
   See the stream-response function for details on how the given handler will be called"
  [params channels get-context handler]
  (let [[input output close-ch] channels
        credentials             (get-credentials params)]
    (async/go-loop []
      (let [[message port] (async/alts! [close-ch input])]
        (cond
          (= port close-ch) (do
                              (async/close! input)
                              (async/close! output)
                              (async/close! close-ch))
          (= port input)    (let [data    (-> (dissoc params :api-key :organization)
                                              (assoc :messages (conj (get-context) message)))
                                  request (create-request credentials data)]
                              (stream-response request output handler)
                              (recur)))))
    close-ch))

(defn initialize*
  [model agent]
  (if-some [functions (vals (:functions agent))]
    (->> (map meta functions)
         (map :json-schema)
         (assoc-in model [:params :functions]))
    model))

(defrecord Gpt [params input output close-ch]
  Model
  (initialize [model agent] (initialize* model agent))
  (send-message! [_ message] (async/put! input message))
  (start! [_ get-context handler] (start* params [input output close-ch] get-context handler))
  (stop!  [_] (async/put! close-ch :closed)))

(defn create-model
  "Create a GPT backed model"
  [params]
  (let [input    (async/chan)
        output   (async/chan)
        close-ch (async/chan)]
    (Gpt. params input output close-ch)))
