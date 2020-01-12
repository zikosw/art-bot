(ns bitkub.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [re-frame.core :as rf]
    [art-bot.events :as e]
    [cljs.core.async :as a :refer [<! >! alt! chan timeout close!]]
    [utils.json :as j]
    [haslett.client :as ws]))

(def websocket-url "wss://api.bitkub.com/websocket-api")


(defn connect [coin market]
  (go
    (let [url (str websocket-url "/market.ticker." (name market) "_" (name coin))] 
      (<! (ws/connect url {:format j/haslett-json})))))



(comment


  (def conn (connect :usdt :thb))
  (def ex (e/run conn [:bitkub/ticker :usdt :thb]))

  (def conn (connect :btc :thb))
  (def ex (e/run conn [:bitkub/ticker :btc :thb])))


(rf/reg-event-db
  :bitkub/ticker-start
  (fn [db [_ coin market]]
    (let [conn (connect coin market) 
          exit-ch (e/run conn [:bitkub/ticker coin market])]
      (assoc-in db [:bitkub/stream coin market] exit-ch))))

