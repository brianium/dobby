(ns clj-kondo.dobby
  (:require [clj-kondo.hooks-api :as api]))

(defn defunction-hook
  "Transforms a defunction into a defn"
  [{:keys [node]}]
  (let [[name docstring _ binding-vec & body] (rest (:children node))
        [sym val]                                  (:children binding-vec)]
    (when-not (and sym val)
      (throw (ex-info "No sym and val provided" {})))
    (let [new-node (api/list-node
                    (list*
                     (api/token-node 'defn)
                     name
                     docstring
                     (api/vector-node [sym val])
                     body))]
      {:node new-node})))
