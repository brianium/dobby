(ns dobby.model.impl)

(defprotocol Model
  (initialize [_ agent] "Returns an initialized model")
  (send-message! [_ message])
  (start! [_ get-context handler])
  (stop! [_]))

(defn is-model?
  [x]
  (satisfies? Model x))
