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
                      (println data)
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
