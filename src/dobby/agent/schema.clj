(ns dobby.agent.schema
  (:require [dobby.log :as dl]
            [dobby.model :as dm]
            [malli.core :as m]
            [malli.util :as mu]))

(def Agent
  [:map
   [:init fn?]
   [:functions [:map-of :string fn?]]
   [:initial-prompt :string]])

(def StartedAgent
  [:and
   (mu/assoc Agent :id :uuid)
   [:fn (fn [{:keys [model log]}]
          (and (dm/is-model? model)
               (dl/is-log? log)))]])

(defn is-agent?
  [x]
  (m/validate Agent x))

(defn is-started?
  [x]
  (m/validate StartedAgent x))
