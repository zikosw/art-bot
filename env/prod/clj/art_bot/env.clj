(ns art-bot.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[art-bot started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[art-bot has shut down successfully]=-"))
   :middleware identity})
