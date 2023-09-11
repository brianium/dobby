(ns clj-kondo.dobby
  (:require [clj-kondo.hooks-api :as api]))

(defn defagent-hook
  "Transforms a defunction into a defn"
  [{:keys [node]}]
  (let [[name _ _ binding-vec & body] (rest (:children node))
        [agent]                       (:children binding-vec)]
    (when-not agent
      (throw (ex-info "No agent binding provided" {})))
    (let [new-node (api/list-node
                    (list
                     (api/token-node 'def)
                     name
                     (api/list-node
                      (list
                       (api/token-node 'fn)
                       (api/list-node
                        (list
                         (api/vector-node [_ _ _])
                         (api/list-node
                          (list*
                           (api/token-node 'fn)
                           (api/vector-node [agent])
                           body))))
                       (api/list-node
                        (list
                         (api/vector-node [_ _])
                         (api/list-node
                          (list*
                           (api/token-node 'fn)
                           (api/vector-node [agent])
                           body))))))))] 
      {:node new-node})))

(defn defunction-hook
  "Transforms a defunction into a defn"
  [{:keys [node]}]
  (let [[name docstring _ binding-vec & body] (rest (:children node))
        [sym val]                             (:children binding-vec)]
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
