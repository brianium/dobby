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

(defn stream-hook
  "Transforms a stream into a with-open"
  [{:keys [node]}]
  (let [[_ binding-vec & body] (rest (:children node))
        [sym]                  (:children binding-vec)]
    (when-not (and sym val)
      (throw (ex-info "No sym and val provided" {})))
    (let [new-node (api/list-node
                    (list*
                     (api/token-node 'with-open)
                     (api/vector-node [sym _]) 
                     body))]
      {:node new-node})))
