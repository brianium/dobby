(ns dobby.impl.gpt
  (:require [org.httpkit.client :as http]
            [clojure.tools.logging :refer [error]]
            [clojure.java.io :as io]
            [clojure.core.async :as async]
            [dobby.impl.json :refer [json-decode json-encode]]))

(defn- parse-line
  [line]
  (cond
    (= line "data: [DONE]")
    :done

    (.startsWith line "data: ")
    (-> line
        (subs 6) ;; trim "data: " prefix
        (json-decode))

    :else
    (error line)))

(defn- parse-body
  [bytes]
  (let [rdr (io/reader bytes :encoding "UTF-8")]
    (->> (line-seq rdr)
         (filter not-empty)
         (map parse-line))))

(defn- make-client
  [{:keys [api-key organization]}]
  {:api-key      api-key
   :organization organization})

(def *default-client (delay (make-client {:api-key      (System/getenv "OPEN_AI_KEY")
                                          :organization (System/getenv "OPEN_AI_ORGANIZATION")})))


(defn- get-client
  "If api-key and organization are provided in params, use those to make the client.
   If not provided, the default-client will be used by reading credentials from the environment"
  [params]
  (let [{:keys [api-key organization]} params]
    (if (and api-key organization)
      (make-client {:api-key      api-key
                    :organization organization})
      @*default-client)))

(defn- channel
  "Create a channel to stream responses from the GPT Chat API"
  [params]
  (let [client (get-client params)
        {:keys [api-key organization]} client
        ch (async/chan)
        url "https://api.openai.com/v1/chat/completions"]
    (http/post url
               {:headers {"Authorization"       (str "Bearer " api-key)
                          "Content-Type"        "application/json"
                          "OpenAI-Organization" organization}
                :body    (json-encode (merge (dissoc params :api-key :organization) {:stream true}))
                :as      :stream}
               (fn [{:keys [body]}]
                 (when-let [chunks (seq (parse-body body))]
                   (doseq [chunk chunks]
                     (let [choice    (some-> chunk :choices first)
                           finished? (some? (:finish_reason choice))]
                       (if finished?
                         (async/close! ch)
                         (when (some? choice)
                           (async/put! ch choice))))))))
    ch))

(defn- stream*
  "Stream responses from the Chat GPT API"
  ([messages params]
   (channel (merge {:model "gpt-3.5-turbo" :messages messages} (dissoc params :messages))))
  ([messages]
   (stream* messages {})))

(defn stream
  "Create a stream of responses to the given context"
  ([context params]
   (let [output  (async/chan)
         ch      (stream* context params)]
     (async/go-loop []
       (if-some [chunk (async/<! ch)]
         (let [delta (:delta chunk)]
           (async/put! output delta)
           (recur))
         (async/close! output)))
     output))
  ([context]
   (stream context {})))

(defmulti apply-delta (fn [_ delta]
                        (-> delta
                            keys
                            first)))

(defmethod apply-delta :content [message delta]
  (update message :content str (:content delta)))

(defmethod apply-delta :function_call [message delta]
  (update-in message [:function_call :arguments] str (get-in delta [:function_call :arguments])))

(defmethod apply-delta :default [message delta]
  (merge message delta))

(defn- parse-message
  [message]
  (if (:function_call message)
    (update-in message [:function_call :arguments] json-decode)
    message))

(defn transpose
  "Consume a stream. Writes incremental output to the given core async channel. The output
   channel will receive payloads of the format {:type :text :content <string>}
   
   Returns a map with the final response to be added to context"
  [stream output]
  (async/<!! (async/go-loop [message {}]
               (if-some [delta (async/<! stream)]
                 (do
                   (when-some [content (:content delta)] ;;; We only stream content to the output channel
                     (async/put! output {:type :text :content content}))
                   (recur (apply-delta message delta)))
                 (parse-message message)))))
