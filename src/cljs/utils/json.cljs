(ns utils.json
  (:require 
    [shadow.json :as sw]
    [haslett.format :as fmt]))

(def haslett-json
  "Read and write data encoded in JSON."
  (reify fmt/Format
    (read  [_ s] (sw/to-clj (js/JSON.parse s)))
    (write [_ v] (js/JSON.stringify (clj->js v)))))
