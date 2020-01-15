(ns art-bot.core
  (:require
    [day8.re-frame.http-fx]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [goog.events :as events]
    [goog.history.EventType :as HistoryEventType]
    [markdown.core :refer [md->html]]
    [art-bot.ajax :as ajax]
    [art-bot.events]
    [bitkub.core]
    [binance.core]
    [reitit.core :as reitit]
    [clojure.string :as string])
  (:import goog.History))

(defn nav-link [uri title page]
  [:a.navbar-item
   {:href   uri
    :class (when (= page @(rf/subscribe [:page])) :is-active)}
   title])

(defn navbar []
  (r/with-let [expanded? (r/atom false)]
    [:nav.navbar.is-info>div.container
     [:div.navbar-brand
      [:a.navbar-item {:href "/" :style {:font-weight :bold}} "art-bot"]
      [:span.navbar-burger.burger
       {:data-target :nav-menu
        :on-click #(swap! expanded? not)
        :class (when @expanded? :is-active)}
       [:span][:span][:span]]]
     [:div#nav-menu.navbar-menu
      {:class (when @expanded? :is-active)}
      [:div.navbar-start
       [nav-link "#/" "Home" :home]
       [nav-link "#/about" "About" :about]]]]))

(defn about-page []
  [:section.section>div.container>div.content
   [:img {:src "/img/warning_clojure.png"}]])


(defn brand-color [exchange]
  (case exchange
    :satang-pro "text-blue-700"
    :binance "text-yellow-700"
    :bitkub "text-teal-700"
    "text-gray-600"))

(defn fixed2 [num]
  (-> num js/Number (.toFixed 2)))

(defn price-row [ex pair {:keys [buy sell] :as ticker}]
  [:tr.even:bg-gray-100
   [:td.p-2 {:class (brand-color ex)} (name ex)]
   [:td.p-2.text-center.text-gray-600 (str pair)]
   [:td.p-2.text-right.text-green-500 (fixed2 buy)]
   [:td.p-2.text-right.text-red-500 (fixed2 sell)]
   [:td.p-2.text-right (fixed2 (* 100 (/(- sell buy) buy)))]])

(defn prices []
  [:table.shadow-lg.rounded-lg.table-auto.overflow-hidden.bg-gray-200
   [:thead
    [:tr.bg-gray-300.capitalize.border-b-2.border-gray-600
     [:th.p-2 "exchange"]
     [:th.p-2 "pair"]
     [:th.p-2.w-24 "buy"]
     [:th.p-2.w-24 "sell"]
     [:th.p-2.w-24 "spread"]]]
   [:tbody
    (let [ticker @(rf/subscribe [:satang-pro/ticker-best :btc :thb])]
      [price-row :satang-pro :btc/thb ticker])
    (let [ticker @(rf/subscribe [:bitkub/ticker-best :btc :thb])]
      [price-row :bitkub :btc/thb ticker])
    (let [ticker @(rf/subscribe [:satang-pro/ticker-best :eth :thb])]
      [price-row :satang-pro :eth/thb ticker])
    (let [ticker @(rf/subscribe [:bitkub/ticker-best :eth :thb])]
      [price-row :bitkub :eth/thb ticker])
    (let [ticker @(rf/subscribe [:satang-pro/ticker-best :usdt :thb])]
      [price-row :satang-pro :btc/thb ticker])
    (let [ticker @(rf/subscribe [:bitkub/ticker-best :usdt :thb])]
      [price-row :bitkub :usdt/thb ticker])
    (let [ticker @(rf/subscribe [:binance/ticker-best :btc :usdt])]
      [price-row :binance :btc/usdt ticker])
    (let [ticker @(rf/subscribe [:binance/ticker-best :eth :usdt])]
      [price-row :binance :btc/usdt ticker])]])


(defn art [{:keys [from to steps]}]
 (let [ticker-sub (fn [ex] (keyword ex "ticker-best"))
       side-color (fn[step] (case (:side step) :buy "text-green-500" "text-red-500"))
       get-best   (fn [step sub] (get sub (case (:side step)
                                            :sell :buy
                                            :buy :sell)))
       ->coin     (comp first :pair)
       ->mkt      (comp second :pair)
       make-sub   (fn[step] [(-> step :at ticker-sub) (->coin step) (->mkt step)]) 
       get-price  (fn [step] (get-best step (-> step make-sub rf/subscribe deref)))
       str-pair   (fn [step] (str (apply keyword (:pair step))))
       get-fee    (fn [step] (case (:at step)
                               :binance 0.001
                               0.0025))
       step1      (get steps 0)
       step2      (get steps 1)
       step3      (get steps 2)
       price1     (get-price step1)
       price2     (get-price step2)
       price3     (get-price step3)
       isBuy2     (= :buy (:side step2))
       buy        (* price1 (if isBuy2 price2 1))
       sell       (* price3 (if-not isBuy2 price2 1))
       diff       (- sell buy)
       diff100k   (-> diff (/ buy) (* 100000))
       init-val   100000
       net1       (* (/ init-val price1)
                     (- 1 (get-fee step1)))
       net2       (* (- 1 (get-fee step2))
                    (if isBuy2
                      (/ net1 price2)
                      (* net1 price2)))
       net3       (* (* net2 price3)
                     (- 1 (get-fee step3)))
       net123     (- net3 init-val)]
   [:tr
    [:td {:class (brand-color from)} (str from)]
    [:td {:class (brand-color to)} (str to)]
    [:td.p-2
     {:class (side-color step1)} 
     [:p (str-pair step1)]
     [:p.font-semibold (fixed2 price1)]
     [:p.text-gray-600 (fixed2 net1)]]
    [:td.p-2
     {:class (side-color step2)} 
     [:p (str-pair step2)]
     [:p.font-semibold (fixed2 price2)]
     [:p.text-gray-600 (fixed2 net2)]]
    [:td.p-2
     {:class (side-color step3)}
     [:p (str-pair step3)]
     [:p.font-semibold (fixed2 price3)]
     [:p.text-gray-600 (fixed2 net3)]]
    [:td.p-2.w-24.text-right (fixed2 buy)]
    [:td.p-2.w-24.text-right (fixed2 sell)]
    [:td.p-2.w-24.text-right
     {:class ["font-bold" (if (> diff 0) "text-green-500" "text-red-500")]}
     (fixed2 diff100k)]
    [:td.p-2.w-24.text-right
     {:class ["font-bold" (if (> net123 0) "text-green-500" "text-red-500")]}
     (fixed2 net123)]]))


(defn arbitrage []
  [:div
    [:h3.py-2.text-lg.font-bold.text-white "Arbitrage"]
    [:table.shadow-lg.rounded-lg.table-auto.overflow-hidden.text-center
      [:thead.capitalize
       [:tr.bg-gray-300
        [:th.p-2 "from"]
        [:th.p-2 "to"]
        [:th.p-2 "start"]
        [:th.p-2 "medium"]
        [:th.p-2 "final"]
        [:th.p-2 "buy"]
        [:th.p-2 "sell"]
        [:th.p-2 "100k THB"]
        [:th.p-2 "- fees"]]]
      [:tbody.bg-gray-100
       [art {:from :binance
             :to   :satang-pro
             :steps [{:pair [:usdt  :thb] :side :buy :at :satang-pro}
                     {:pair [:eth  :usdt] :side :buy :at :binance}
                     {:pair [:eth :thb ]  :side :sell :at :satang-pro}]}]
       [art {:from :binance
             :to   :bitkub
             :steps [{:pair [:usdt  :thb] :side :buy :at :bitkub}
                     {:pair [:eth  :usdt] :side :buy :at :binance}
                     {:pair [:eth :thb ]  :side :sell :at :bitkub}]}]
       [art {:from  :satang-pro
             :to    :binance
             :steps [{:pair [:eth  :thb]  :side :buy :at :satang-pro}
                     {:pair [:eth  :usdt] :side :sell :at :binance}
                     {:pair [:usdt :thb ] :side :sell :at :satang-pro}]}]
       [art {:from  :bitkub
             :to    :binance
             :steps [{:pair [:eth  :thb]  :side :buy :at :bitkub}
                     {:pair [:eth  :usdt] :side :sell :at :binance}
                     {:pair [:usdt :thb ] :side :sell :at :bitkub}]}]
       [art {:from  :binance
             :to    :satang-pro
             :steps [{:pair [:usdt  :thb] :side :buy :at :satang-pro}
                     {:pair [:btc  :usdt] :side :buy :at :binance}
                     {:pair [:btc :thb ]  :side :sell :at :satang-pro}]}]
       [art {:from  :binance
             :to    :bitkub
             :steps [{:pair [:usdt  :thb] :side :buy :at :bitkub}
                     {:pair [:btc  :usdt] :side :buy :at :binance}
                     {:pair [:btc :thb ]  :side :sell :at :bitkub}]}]
       [art {:from  :satang-pro
             :to    :binance
             :steps [{:pair [:btc  :thb]  :side :buy :at :satang-pro}
                     {:pair [:btc  :usdt] :side :sell :at :binance}
                     {:pair [:usdt :thb ] :side :sell :at :satang-pro}]}]
       [art {:from  :bitkub
             :to    :binance
             :steps [{:pair [:btc  :thb]  :side :buy :at :bitkub}
                     {:pair [:btc  :usdt] :side :sell :at :binance}
                     {:pair [:usdt :thb ] :side :sell :at :bitkub}]}]]]])

(defn depth-books [books component]
  [:div
   (for [[price qty] books] ;; TODO: FOR [SELL] need to drop until 10 left, not take 10
     ^{:key price} [component price qty])])

(defn depth [exchange coin]
  (let [is-start  (r/atom false)]
    (fn []
      (let [market :usdt
            usdtthb @(rf/subscribe [:bitkub/ticker-best :usdt :thb])
            usdt-sell (:sell usdtthb)
            usdt-buy  (:buy usdtthb)
            data (:data @(rf/subscribe [:depth exchange coin market]))
            qty-not-zero (fn [[_ qty]] (not= qty "0.00000000"))
            bids (->> (:b data) (filter qty-not-zero) (take 10))
            asks (->> (:a data) (filter qty-not-zero) (take 10) reverse)
            bid-com (fn [p q]
                      (let [num-q (js/Number q)
                            num-p (js/Number p)]
                        [:div.flex.relative
                         [:p {:class "absolute top-0 rigth-0 z-10 h-full max-w-full bg-green-900 opacity-50"
                              :style {:width (str (* 10 num-q) "%")}}]
                         [:p {:class "w-1/6 z-20 font-thin text-green-600"} (.toFixed (* p usdt-sell) 2)]
                         [:p {:class "w-1/6 z-20 font-thin text-green-600"} (.toFixed num-p 2)]
                         [:p {:class "w-1/6 z-20 text-right"} (.toFixed num-q 4)]
                         [:p {:class "w-1/6 z-20 text-right"} (-> (* num-p num-q)
                                                                  (.toFixed 2))]
                         [:p {:class "w-2/6 z-20 text-right"} (-> (* num-p num-q usdt-sell)
                                                                  (.toFixed 2))]]))
            ask-com (fn [p q]
                      (let [num-q (js/Number q)
                            num-p (js/Number p)]
                        [:div.flex.relative
                         [:p {:class "absolute top-0 rigth-0 z-10 h-full max-w-full bg-red-900 opacity-50"
                              :style {:width (str (* 10 (js/Number q)) "%")}}]
                         [:p {:class "w-1/6 z-20 font-thin text-red-600"} (.toFixed (* p usdt-buy) 2)]
                         [:p {:class "w-1/6 z-20 font-thin text-red-600"} (.toFixed num-p 2)]
                         [:p {:class "w-1/6 z-20 text-right"} (.toFixed num-q 4)]
                         [:p {:class "w-1/6 z-20 text-right"} (-> (* num-p num-q)
                                                                  (.toFixed 2))]
                         [:p {:class "w-2/6 z-20 text-right"} (-> (* num-p num-q usdt-buy)
                                                                (.toFixed 2))]]))]

        (if-not @is-start
          [:button.mt-6.px-4.py-2.text-xl.text-white.bg-red-600.rounded-lg.shadow-md
           {:on-click #(do
                         (rf/dispatch [:binance/depth-start :btc :usdt])
                         (reset! is-start true))}
           "Open Depth Books"]
          [:div.p-6.text-gray-300
           [:div
            [:div.flex
             [:p {:class "w-1/5"} "Price (THB)"]
             [:p {:class "w-1/5"} "Price (USDT)"]
             [:p {:class "w-1/5 text-right"} "Amount (BTC)"]
             [:p {:class "w-1/5 text-right"} "Total (UST)"]
             [:p {:class "w-2/5 text-right"} "Total (THB)"]]
            [depth-books asks ask-com]]
           [:div
            [depth-books bids bid-com]]])))))




(defn home-page []
  [:div.p-8.mx-auto.font-mono
   [:div
    [:h3.text-lg ";; todo - need trade fee"]
    [:h3.text-lg ";; todo - need withdrawal fee"]
    [:div.p-2]
    [:div.py-4
     [prices]
     [arbitrage]
     [(depth :binance :btc)]]]])
     

(def pages
  {:home #'home-page
   :about #'about-page})

(defn page []
  [:div
   ;[navbar]
   [(pages @(rf/subscribe [:page]))]])

;; -------------------------
;; Routes

(def router
  (reitit/router
    [["/" :home]
     ["/about" :about]]))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
      HistoryEventType/NAVIGATE
      (fn [event]
        (let [uri (or (not-empty (string/replace (.-token event) #"^.*#" "")) "/")]
          (rf/dispatch
            [:navigate (reitit/match-by-path router uri)]))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn ^:dev/after-load mount-components []
  (rf/clear-subscription-cache!)
  (r/render [#'page] (.getElementById js/document "app")))

(defn polling-interval []
  (rf/dispatch [:satang-pro/ticker-fetch :btc :thb])
  (rf/dispatch [:satang-pro/ticker-fetch :eth :thb])
  (rf/dispatch [:satang-pro/ticker-fetch :usdt :thb])
  (js/setInterval
    #(do
      (rf/dispatch [:satang-pro/ticker-fetch :btc :thb])
      (rf/dispatch [:satang-pro/ticker-fetch :eth :thb])
      (rf/dispatch [:satang-pro/ticker-fetch :usdt :thb]))
    (* 10 1000)))

(defn init! []
  (rf/dispatch-sync [:navigate (reitit/match-by-name router :home)])
  
  (ajax/load-interceptors!)
  ;; (rf/dispatch [:fetch-docs])
  (hook-browser-navigation!)
  (mount-components)
  (polling-interval)
  (rf/dispatch [:bitkub/ticker-start :usdt :thb])
  (rf/dispatch [:bitkub/ticker-start :btc :thb])
  (rf/dispatch [:bitkub/ticker-start :eth :thb])
  (rf/dispatch [:bitkub/ticker-start :usdt :thb])
  (rf/dispatch [:binance/ticker-start :btc :usdt])
  (rf/dispatch [:binance/ticker-start :eth :usdt]))

