(ns dobby.core
  (:require [dobby.impl.agent :as agent])
  (:refer-clojure :exclude [agent]))

(defn agent
  "Creates an agent. An agent is a bidrectional core.async channel with a stateful log. When read from,
   messages are structured as s tuple of [type data]. These messages are useful for different cases such as streaming vs
   waiting for complete messages.
   
   All messages put on the agent will be sent to gpt. All inputs and their responses will be automatically stored in the
   agent's log
   
   Agents implement clojure.lang.Seqable as a means to read from the log.
   
   Agents can be created with different inputs. The most basic agent is created with a vector representing
   the complete log:
   
   ```clojure
   (agent [{:role \"system\" :content \"You are a helpful assistant\"}])
   ```

   The vector can be skipped and the initial prompt message itself can be given:

   ```clojure
   (agent {:role \"system\" :content \"You are a helpful assistant\"})
   ```
   
   Strings can be used to create an agent with an initial prompt:
   
   ```clojure
   (agent \"You are a helpful assistant\")
   ```
   
   A resource can also be handy for writing prompts in text files:

   ```clojure
   (agent (clojure.java.io/resource \"prompt.txt\"))
   ```
   
   All model parameters can be passed to gpt as keyword arguments (or as a map):
   
   ```clojure
   (agent \"You are a helpful assistant\" :model \"gpt-3.5-turbo\")
   (agent \"You are a helpful assistant\" {:model \"gpt-3.5-turbo\"})
   ```"
  [x & {:as opts}]
  (agent/create x opts))
