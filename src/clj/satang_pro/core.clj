(ns satang-pro.core
  (:require [clj-http.client :as client]))


(def base-url "https://api.tdax.com")


(defn marketcap []
  (client/get (str base-url "/marketcap") {:as :json}))


(defn order-stack [{:keys [pair side limit offset] :as params}]
  (client/get (str base-url "/api/orders/stack")
              {:as :json
               :query-params params}))


(defn books [{:keys [pair limit offset] :as params}]
  (client/get (str base-url "/api/orders/stack")
              {:as :json
               :query-params params}))



(comment
  (books {:pair "btc_thb"})
  (order-stack {:pair "btc_thb" :side "buy"})
  (order-stack {:pair "btc_thb" :side "sell"})
  (client/get "https://api.tdax.com/api/orders" {:as :json}))
