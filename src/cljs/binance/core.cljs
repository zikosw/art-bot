(ns binance.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [art-bot.events :as e]
    [re-frame.core :as rf]
    [cljs.core.async :as a :refer [<! >! alt! chan timeout close!]]
    [haslett.client :as ws]
    [haslett.format :as fmt]))

(def websocket-url "wss://stream.binance.com:9443")

(defn connect [coin market]
  (go
    (let [url (str websocket-url "/ws/" "BTCUSD@ticker") ;; use coin market here
          stream (<! (ws/connect url {:format fmt/json}))]
      (>! (:sink stream) {"method" "SUBSCRIBE"
                          "params" ["btcusdt@ticker"];; use coin market here
                          "id" 1})
      stream)))


(defn close [exit-ch]
  (go (ws/close (<! exit-ch))))

(comment

  (def conn (connect :btc :usdt))
  (def ex (e/run conn [:binance/ticker :btc :usdt]))


  (go (println (<! conn)))
  (def r (run conn))
  (println :r r)
  (go (ws/close (<! r)))

    
  (go (println (<! stream)))
  (go (println (<! stream)))

  ;;(def ex (go (run :binance/ticker (:source (<! stream)))))
  (def x (go :1))
  (println x)
  (go (println (<! x)))
  (go
    (>! (<! ex) :bye))
  (ws/close stream))

(rf/reg-event-db
  :binance/ticker-start
  (fn [db [_ coin market]]
    (let [conn (connect coin market)
          exit-ch (e/run conn [:binance/ticker coin market])]
      (assoc-in db [:binance/stream coin market] exit-ch))))

(rf/reg-event-db
  :binance/ticker
  (fn [db [_ data]]
    ;; {"stream":"market.ticker.thb_btc","id":1,"last":"240000.00","lowestAsk":"240000.00","highestBid":"239324.00","change":2000,"percentChange":"0.84","baseVolume":"198.17639439","quoteVolume":"46653708.01","isFrozen":"0","high24hr":"240000.00","low24hr":"230000.00","open":236089,"close":235800}
    (assoc db :binance/ticker data)))
