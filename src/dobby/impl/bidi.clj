(ns dobby.impl.bidi
  (:require [clojure.core.async.impl.protocols :as async.proto]))

(defrecord BidiChan [input output]
  async.proto/ReadPort
  (take! [_ handler]
    (async.proto/take! output handler))

  async.proto/WritePort
  (put! [_ val handler]
    (async.proto/put! input val handler))

  async.proto/Channel
  (close! [_]
    (async.proto/close! input)
    (async.proto/close! output))
  (closed? [_]
    (async.proto/closed? input)))

(defn bidi-chan
  "Create a bidrectional channel from an input and output channel"
  [input output]
  (BidiChan. input output))
