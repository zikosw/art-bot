(ns binance.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [art-bot.events :as e]
    [re-frame.core :as rf]
    [cljs.core.async :as a :refer [<! >! alt! chan timeout close!]]
    [utils.json :as j]
    [haslett.client :as ws]))

(def websocket-url "wss://stream.binance.com:9443")



(defn stream-ticker [coin market]
  (go
    ;; (let [url (str websocket-url "/ws/" "BTCUSD@ticker")]) ;; use coin market here
    (let [url (str websocket-url "/ws/" (str (name coin) (name market) "@ticker")) ;; use coin market here
          stream (<! (ws/connect url {:format j/haslett-json}))]
      (>! (:sink stream) {"method" "SUBSCRIBE"
                          "params" [(str (name coin) (name market) "@ticker")];; use coin market here
                          "id" 1})
      stream)))

(defn stream-depth [coin market]
  (go
    (let [url (str websocket-url "/stream?streams=" (name coin) (name market) "@depth")]
      (<! (ws/connect url {:format j/haslett-json})))))

(defn close [exit-ch]
  (go (ws/close (<! exit-ch))))

(comment

  (def conn (connect :btc :usdt))
  (def ex (e/run conn [:binance/ticker :btc :usdt]))



  (def ex (e/run dp [:binance/depth :btc :usdt])))



(rf/reg-event-db
  :binance/ticker-start
  (fn [db [_ coin market]]
    (let [conn (stream-ticker coin market)
          exit-ch (e/run conn [:binance/ticker coin market])]
      (assoc-in db [:binance/stream-ticker coin market] exit-ch))))

(rf/reg-event-db
  :binance/depth-start
  (fn [db [_ coin market]]
    (let [conn (stream-depth coin market)
          exit-ch (e/run conn [:binance/depth coin market])]
      (assoc-in db [:binance/stream-ticker coin market] exit-ch))))

