(ns art-bot.app
  (:require [art-bot.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
