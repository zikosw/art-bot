(ns bitkub.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [re-frame.core :as rf]
    [art-bot.events :as e]
    [cljs.core.async :as a :refer [<! >! alt! chan timeout close!]]
    [haslett.client :as ws]
    [haslett.format :as fmt]))

(def websocket-url "wss://api.bitkub.com/websocket-api")


(defn connect [coin market]
  (go
    (let [url (str websocket-url "/market.ticker." (name market) "_" (name coin))] 
      (<! (ws/connect url {:format fmt/json})))))



(comment


  (def conn (connect :usdt :thb))
  (def ex (e/run conn [:bitkub/ticker :usdt :thb]))

  (def conn (connect :btc :thb))
  (def ex (e/run conn [:bitkub/ticker :btc :thb]))

  (go
    (def stream (<! (ws/connect "wss://api.bitkub.com/websocket-api/market.ticker.thb_btc" {:format fmt/json}))))
  (def ex (run :bitkub/ticker (:source stream)))
  (println ex)
  (go
    (>! ex :bye))
  (go (js/console.log (<! ex))) 
  (rf/dispatch [:bitkub/ticker {:data :eie}])
  (print stream)
  (go
    (doseq [n (range 10)]
      (js/console.log n (<! (:source stream)))))
  (ws/close stream)
  (go (let [stream (<! (ws/connect "wss://api.bitkub.com/websocket-api/market.ticker.thb_btc"))]
        ;(>! (:sink stream) "Hello World")
        (js/console.log 1 (<! (:source stream)))
        (js/console.log 2 (<! (:source stream))))))
        ;(ws/close stream))))

(rf/reg-event-db
  :bitkub/ticker-start
  (fn [db [_ coin market]]
    (let [conn (connect coin market) 
          exit-ch (e/run conn [:bitkub/ticker coin market])]
      (assoc-in db [:bitkub/stream coin market] exit-ch))))

(rf/reg-event-db
  :bitkub/ticker
  (fn [db [_ data]]
    ;; {"stream":"market.ticker.thb_btc","id":1,"last":"240000.00","lowestAsk":"240000.00","highestBid":"239324.00","change":2000,"percentChange":"0.84","baseVolume":"198.17639439","quoteVolume":"46653708.01","isFrozen":"0","high24hr":"240000.00","low24hr":"230000.00","open":236089,"close":235800}
    (assoc db :bitkub/ticker data)))
