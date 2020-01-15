(ns art-bot.events
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [cljs.core.async :as a :refer [<! alt! chan timeout]]
    [haslett.client :as ws]
    [re-frame.core :as rf]
    [ajax.core :as ajax]))

(defn stream->rf [rf-path from-ch]
  (let [exit-ch (chan)]
    (go-loop []
      (alt!
         (timeout 1000) (recur)
         from-ch ([data]
                  (if data
                    (do
                      (println :received rf-path)
                      (rf/dispatch [:set rf-path data])
                      (recur))
                    (println :exit-on-nil)))
         exit-ch (println :exit-ch))) ;; still need to manually call ws/close
    exit-ch))

(defn run [conn rf-path]
  (go
    (let [stream (<! conn)]
      (println rf-path :stream stream)
      (stream->rf rf-path (:source stream))
      stream)))

(defn close [exit-ch]
  (go (ws/close (<! exit-ch))))

;;dispatchers

(rf/reg-event-db
  :set
  (fn [db [_ path data]]
    (assoc-in db path data)))

(rf/reg-event-db
  :navigate
  (fn [db [_ route]]
    (assoc db :route route)))

(rf/reg-event-db
  :set-docs
  (fn [db [_ docs]]
    (assoc db :docs docs)))

(rf/reg-event-fx
  :fetch-docs
  (fn [_ _]
    {:http-xhrio {:method          :get
                  :uri             "/docs"
                  :response-format (ajax/raw-response-format)
                  :on-success       [:set-docs]}}))

(rf/reg-event-db
  :common/set-error
  (fn [db [_ error]]
    (assoc db :common/error error)))






;;subscriptions

(rf/reg-sub
  :route
  (fn [db _]
    (-> db :route)))

(rf/reg-sub
  :page
  :<- [:route]
  (fn [route _]
    (-> route :data :name)))

(rf/reg-sub
  :docs
  (fn [db _]
    (:docs db)))

(rf/reg-sub
  :common/error
  (fn [db _]
    (:common/error db)))

(rf/reg-sub
  :ticker
  (fn [db [_ exchange coin market]]
    (get-in db [(keyword (name exchange) "ticker") coin market])))

(rf/reg-sub
  :depth
  (fn [db [_ exchange coin market]]
    (get-in db [(keyword (name exchange) "depth") coin market])))

(comment
  (get-in @(rf/subscribe [:depth :binance :btc :usdt]) [:data :b])
  (get-in @(rf/subscribe [:depth :binance :btc :usdt]) [:data :a]))

(rf/reg-sub
  :bitkub/ticker-best
  (fn [[_ coin market] _]
    [(rf/subscribe [:ticker :bitkub coin market])])

  ;; Computation Function
  (fn [[ticker] _]
    {:buy  (js/Number (get ticker :highestBid))
     :sell (js/Number (get ticker :lowestAsk))}))

(rf/reg-sub
  :binance/ticker-best
  (fn [[_ coin market] _]
    [(rf/subscribe [:ticker :binance coin market])])

  ;; Computation Function
  (fn [[ticker] _]
    {:buy  (js/Number (get ticker :b))
     :sell (js/Number (get ticker :a))}))

(comment

  (let [exchange :binance
        coin :btc
        market :thb]
    [(keyword (name exchange) "ticker") coin market])
  (rf/subscribe [:ticker :bitkub :btc :thb])
  (rf/subscribe [:ticker :binance :btc :thb])

  (rf/subscribe [:bitkub/ticker-best :btc :thb])
  (rf/subscribe [:bitkub/ticker-best :usdt :thb])
  (rf/subscribe [:binance/ticker-best :btc :usdt])

  (rf/dispatch [:bitkub/ticker-start :btc :thb])


  (rf/subscribe [:bitkub/ticker :btc :thb])
  (get-in
    (deref re-frame.db/app-db)
    [:bitkub/ticker :btc :thb "lowestAsk"]))




(rf/reg-event-fx
  :satang-pro/ticker-fetch
  (fn [_ [_ kw-coin kw-market]]
    (let [coin (name kw-coin)
          market (name kw-market)]
      {:http-xhrio {:method          :get
                    :uri             (str "https://api.tdax.com/api/orders/stack?limit=10&offset=0&pair=" coin "_" market)
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success       [:set [:satang-pro/ticker kw-coin kw-market]]}})))

(rf/reg-sub
  :satang-pro/ticker-best
  (fn [[_ coin market] _]
    [(rf/subscribe [:ticker :satang-pro coin market])])

  ;; Computation Function
  (fn [[ticker] _]
    {:buy  (js/Number (get-in ticker [:bid 0 :price]))
     :sell (js/Number (get-in ticker [:ask 0 :price]))}))

(comment
  (rf/dispatch [:satang-pro/ticker-fetch :btc :thb])
  (rf/dispatch [:satang-pro/ticker-fetch :eth :thb])
  (rf/dispatch [:satang-pro/ticker-fetch :usdt :thb])
  (rf/subscribe [:satang-pro/ticker-best :btc :thb]))
