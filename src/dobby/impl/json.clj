(ns dobby.impl.json
  (:require [cheshire.core :as json]))

(defn json-decode
  [s]
  (json/decode s keyword))

(defn json-encode
  [x]
  (json/generate-string x))
