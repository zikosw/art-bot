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


(defn price-row [ex pair {:keys [buy sell] :as ticker}]
  [:tr.even:bg-gray-100
    [:td.p-2 (name ex)]
    [:td.p-2.text-center (str pair)] 
    [:td.p-2.text-right (str buy)]
    [:td.p-2.text-right (str sell)]])

(defn fixed2 [number]
  (.toFixed number 2))

(defn art [{:keys [from to steps]}]
 (let [ticker-sub (fn [ex] (keyword ex "ticker-best"))
       side-color (fn[step] (case (:side step) :buy "text-green-500" "text-red-500"))
       get-best   (fn [step sub] (get sub (:side step)))
       ->coin     (comp first :pair)
       ->mkt      (comp second :pair)
       make-sub   (fn[step] [(-> step :at ticker-sub) (->coin step) (->mkt step)]) 
       get-price  (fn [step] (get-best step (-> step make-sub rf/subscribe deref)))
       str-pair   (fn [step] (str (apply keyword (:pair step))))
       step1      (get steps 0)
       step2      (get steps 1)
       step3      (get steps 2)
       price1     (get-price step1)
       price2     (get-price step2)
       price3     (get-price step3)
       isBuy2     (= :buy (:side step2))
       buy        (* price1 (if isBuy2 price2 1))
       sell       (* price3 (if-not isBuy2 price2 1))
       diff       (- sell buy)]
   [:tr
    [:td.text-green-800 (str from)]
    [:td.text-yellow-700 (str to)] 
    [:td.p-2
     {:class (side-color step1)} 
     [:p (str-pair step1)]
     [:p.font-semibold (fixed2 price1)]] 
    [:td.p-2
     {:class (side-color step2)} 
     [:p (str-pair step2)]
     [:p.font-semibold (fixed2 price2)]]
    [:td.p-2
     {:class (side-color step3)} 
     [:p (str-pair step3)]
     [:p.font-semibold (fixed2 price3)]]
    [:td.p-2.w-24.text-right (fixed2 buy)] 
    [:td.p-2.w-24.text-right (fixed2 sell)]
    [:td.p-2.w-24.text-right
     {:class ["font-bold" (if (> diff 0) "text-green-500" "text-red-500")]} 
     (fixed2 diff)]
    [:td.p-2.w-24.text-right
     {:class ["font-bold" (if (> diff 0) "text-green-500" "text-red-500")]} 
     (fixed2 (-> diff (/ buy) (* 100000)))]]))


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
        [:th.p-2 "+/-"]
        [:th.p-2 "100k THB"]]]
      [:tbody.bg-gray-200
       [art {:from :binance
             :to   :bitkub
             :steps [{:pair [:usdt  :thb] :side :buy :at :bitkub}
                     {:pair [:eth  :usdt] :side :buy :at :binance}
                     {:pair [:eth :thb ]  :side :sell :at :bitkub}]}]
       [art {:from  :bitkub
             :to    :binance
             :steps [{:pair [:eth  :thb]  :side :buy :at :bitkub}
                     {:pair [:eth  :usdt] :side :sell :at :binance}
                     {:pair [:usdt :thb ] :side :sell :at :bitkub}]}]
       [art {:from  :binance
             :to    :bitkub
             :steps [{:pair [:usdt  :thb] :side :buy :at :bitkub}
                     {:pair [:btc  :usdt] :side :buy :at :binance}
                     {:pair [:btc :thb ]  :side :sell :at :bitkub}]}]
       [art {:from  :bitkub
             :to    :binance
             :steps [{:pair [:btc  :thb]  :side :buy :at :bitkub}
                     {:pair [:btc  :usdt] :side :sell :at :binance}
                     {:pair [:usdt :thb ] :side :sell :at :bitkub}]}]]]])

(defn depth-books [books component]
  [:div
   (for [[price qty] books ;; TODO: FOR [SELL] need to drop until 10 left, not take 10
         :when (not= qty "0.00000000")] ;; TODO: qty=0 is use for delete the old order
     ^{:key price} [component price qty])])

(defn depth []
  (let [data (:data @(rf/subscribe [:depth :binance :btc :usdt]))
        bids (:b data)
        asks (:a data)
        bid-com (fn [p q]
                  [:div.flex.relative
                   [:p {:class "absolute top-0 rigth-0 z-10 h-full max-w-full bg-green-900 opacity-50"
                        :style {:width (str (* 10 (js/Number q)) "%")}}]
                   [:p {:class "w-1/3 z-20 font-thin text-green-600"} (str p)]
                   [:p {:class "w-1/3 z-20 text-right"} (str q)]
                   [:p {:class "w-1/3 z-20 text-right"} (-> (* (js/Number p) (js/Number q))
                                                            (.toFixed 8))]])
        ask-com (fn [p q]
                  [:div.flex.relative
                   [:p {:class "absolute top-0 rigth-0 z-10 h-full max-w-full bg-red-900 opacity-50"
                        :style {:width (str (* 10 (js/Number q)) "%")}}]
                   [:p {:class "w-1/3 z-20 text-red-600"} (str p)]
                   [:p {:class "w-1/3 z-20 text-right"} (str q)]
                   [:p {:class "w-1/3 z-20 text-right"} (-> (* (js/Number p) (js/Number q))
                                                          (.toFixed 8))]])]
    [:div.p-6.text-gray-300
     [:div
      [:div.flex
       [:p {:class "w-1/3"} "Price (USDT)"]
       [:p {:class "w-1/3 text-right"} "Amount (BTC)"]
       [:p {:class "w-1/3 text-right"} "Total (UST)"]]
      [depth-books asks ask-com]]
     [:div
      [depth-books bids bid-com]]]))



(defn prices []
  [:table.shadow-lg.rounded-lg.table-auto.overflow-hidden
   [:thead
    [:tr.bg-gray-300.capitalize.border-b-2.border-gray-600
     [:th.p-2 "exchange"]
     [:th.p-2 "pair"]
     [:th.p-2.w-24 "buy"]
     [:th.p-2.w-24 "sell"]]]
   [:tbody
    (let [ticker @(rf/subscribe [:bitkub/ticker-best :btc :thb])]
      [price-row :bitkub :btc/thb ticker])
    (let [ticker @(rf/subscribe [:bitkub/ticker-best :usdt :thb])]
      [price-row :bitkub :usdt/thb ticker])
    (let [ticker @(rf/subscribe [:binance/ticker-best :btc :usdt])]
      [price-row :binance :btc/usdt ticker])]])

(defn home-page []
  [:div.absolute.inset-0.w-full.h-full.p-8.mx-auto.font-mono.bg-gray-800
   [:div
    [:h3.text-lg ";; todo - need trade fee"]
    [:h3.text-lg ";; todo - need withdrawal fee"]
    [:div.p-2]
    [:div.py-4
     #_[arbitrage]
     [depth]]]])
     

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

(defn init! []
  (rf/dispatch-sync [:navigate (reitit/match-by-name router :home)])
  
  (ajax/load-interceptors!)
  ;; (rf/dispatch [:fetch-docs])
  (hook-browser-navigation!)
  (mount-components)
  (rf/dispatch [:binance/depth-start :btc :usdt])
  (rf/dispatch [:bitkub/ticker-start :usdt :thb])
  (rf/dispatch [:bitkub/ticker-start :btc :thb])
  (rf/dispatch [:bitkub/ticker-start :eth :thb])
  (rf/dispatch [:bitkub/ticker-start :usdt :thb])
  (rf/dispatch [:binance/ticker-start :btc :usdt])
  (rf/dispatch [:binance/ticker-start :eth :usdt]))

