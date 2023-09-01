(ns dobby.model.gpt.schema
  (:require [malli.util :as mu]))

(def Credentials
  [:map {:closed true}
   [:api-key :string]
   [:organization :string]])

(def Params
  (-> Credentials 
      (mu/optional-keys [:api-key :organization])
      (mu/merge [:map {:closed true}
                 [:model :string]])))

(def FunctionCall
  [:map {:closed true}
   [:name :string]
   [:arguments :string]])

(def Message
  [:map
   [:role [:enum "system" "user" "assistant" "function"]]
   [:content [:maybe :string]]
   [:name {:optional true} :string]
   [:function_call {:optional true} FunctionCall]])
