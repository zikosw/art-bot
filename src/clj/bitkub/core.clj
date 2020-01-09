(ns bitkub.core
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [gniazdo.core :as ws]
            [clojure.core.async :as a]))


(def base-url "https://api.bitkub.com")


;; 1, // order id
;; 1529453033, // timestamp
;; "997.50", // volume
;; "10000.00", // rate
;; "0.09975000" // amount

(defn ->norm-book [[oid timestamp vol price amount]]
  {:price  (str price)
   :amount (str amount)})

(defn books [{:keys [sym lmt] :as params}]
  (let [resp (client/get (str base-url "/api/market/books")
                        {:as :json
                         :query-params params})
        res  (get-in resp [:body :result])]
    {:ask (->> (:asks res) (map ->norm-book))
     :bid (->> (:bids res) (map ->norm-book))}))


(comment
  (books {:sym "THB_BTC" :lmt 10}))

(def websocket-url "wss://api.bitkub.com/websocket-api")
;;(def websocket-url "wss://api.bitkub.com/websocket-api/market.ticker.thb_btc")

(defn stream-ticker []
  (ws/connect
    "wss://api.bitkub.com/websocket-api/market.ticker.thb_btc"
    :on-receive #(prn 'received (json/parse-string %))))

(comment
  (def socket
    (ws/connect
      "wss://api.bitkub.com/websocket-api/market.ticker.thb_btc"
      :on-receive #(prn 'received %)))
  (ws/send-msg socket "hello")
  (ws/close socket))
