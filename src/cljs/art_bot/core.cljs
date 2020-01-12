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



(rf/reg-sub
  :ticker
  (fn [db [_ exchange coin market]]
    (get-in db [(keyword (name exchange) "ticker") coin market])))

(rf/reg-sub
  :bitkub/ticker-best
  (fn [[_ coin market] _]
    [(rf/subscribe [:ticker :bitkub coin market])])

  ;; Computation Function
  (fn [[ticker] _]
    {:buy  (js/Number (get ticker "highestBid"))
     :sell (js/Number (get ticker "lowestAsk"))}))

(rf/reg-sub
  :binance/ticker-best
  (fn [[_ coin market] _]
    [(rf/subscribe [:ticker :binance coin market])])

  ;; Computation Function
  (fn [[ticker] _]
    {:buy  (js/Number (get ticker "b"))
     :sell (js/Number (get ticker "a"))}))

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

(defn home-page []
  [:section.section>div.container>div.content
   (let [docs @(rf/subscribe [:docs])]
         
     [:div
      [:h3 ";; todo - need trade fee"]
      [:h3 ";; todo - need withdrawal fee"]
      (let [{:keys [buy sell]} @(rf/subscribe [:bitkub/ticker-best :btc :thb])]
        [:div
         [:h3 "bitkub btc/thb"]
         [:p "buy: " buy]
         [:p "sell: " sell]])
      (let [{:keys [buy sell]} @(rf/subscribe [:bitkub/ticker-best :usdt :thb])]
        [:div
         [:h3 "bitkub usdt/thb"]
         [:p "buy: " buy]
         [:p "sell: " sell]])
      (let [{:keys [buy sell]} @(rf/subscribe [:binance/ticker-best :btc :usdt])]
        [:div
         [:h3 "binance btc/usdt"]
         [:p "buy: " buy]
         [:p "sell: " sell]])
      (let [btcthb  (:sell @(rf/subscribe [:bitkub/ticker-best :btc :thb]))
            usdtthb (:buy @(rf/subscribe [:bitkub/ticker-best :usdt :thb]))
            btcusd  (:buy @(rf/subscribe [:binance/ticker-best :btc :usdt]))]
        [:div
         [:h3 "buy binance >> sell bitkub"]
         [:p "buy btc/usdt -> sell btc/thb -> buy usdt/thb"]
         [:p "buy btc/usd" btcusd]
         [:p "buy usdt/thb" usdtthb]
         [:p "binance buy btc/usdt : " (* btcusd usdtthb)]
         [:p "bitkub  sell  btc/thb  : " btcthb]])
      (let [btcthb  (:buy @(rf/subscribe [:bitkub/ticker-best :btc :thb]))
            usdtthb (:sell @(rf/subscribe [:bitkub/ticker-best :usdt :thb]))
            btcusd  (:sell @(rf/subscribe [:binance/ticker-best :btc :usdt]))]
        [:div
         [:h3 "buy bitkub >> sell binance"]
         [:p "buy btc/thb -> sell btc/usdt -> sell usdt/thb"]
         [:p "sell btc/usd" btcusd]
         [:p "sell usdt/thb" usdtthb]
         [:p "bitkub  buy  btc/thb  : " btcthb]
         [:p "binance sell btc/usdt : " (* btcusd usdtthb)]])
      [:div {:dangerouslySetInnerHTML {:__html (md->html docs)}}]])])

(def pages
  {:home #'home-page
   :about #'about-page})

(defn page []
  [:div
   [navbar]
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
  (rf/dispatch [:bitkub/ticker-start :btc :thb])
  (rf/dispatch [:bitkub/ticker-start :usdt :thb])
  (rf/dispatch [:binance/ticker-start :btc :usdt])
  
  (ajax/load-interceptors!)
  (rf/dispatch [:fetch-docs])
  (hook-browser-navigation!)
  (mount-components))

