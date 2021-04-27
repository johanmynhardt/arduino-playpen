(ns zoetrope
  (:require [clojure.core.async :as a :refer [<!]]
            [firmata.core :as fc]))

(defonce ctx
  (atom {:board nil
         :motor {:speed 0
                 :state :low}}))

(defn reset-board
  []
  (fc/reset-board (:board @ctx))
  (a/unsub-all (fc/event-publisher (:board @ctx))))

(def hl-switch
  {:high :low
   :low :high})

(defn update-motor
  [motor-pin]
  (let [{:keys [state speed]} (-> @ctx :motor)]
    (fc/set-analog (:board @ctx) motor-pin (if (= :high state) speed 0))))

(def pins
  {:control1 {:pin 2 :mode :output :value :low :hbridge/pin 7}
   :control2 {:pin 3 :mode :output :value :low :hbridge/pin 2}
   :enable   {:pin 9 :mode :pwm :value 0 :hbridge/pin 1}

   :direction {:pin 4 :mode :input :value :low
               :on-event
               (fn [{:keys [value] :as event}]
                 (fc/set-digital (:board @ctx) (-> pins :control1 :pin) value)
                 (fc/set-digital (:board @ctx) (-> pins :control2 :pin) (hl-switch value)))}

   :onOff {:pin 5 :mode :input :value 0
           :on-event
           (fn [{:keys [value] :as event}]
             (swap! ctx assoc-in [:motor :state] value)
             (update-motor (-> pins :enable :pin)))}

   :pot {:pin 0 :mode :analog :value 0
         :on-event
         (fn [{:keys [value] :as event}]
           (swap! ctx assoc-in [:motor :speed] (if (pos? value) (int (/ value 4)) 0))
           (update-motor (-> pins :enable :pin)))}})

(defn hook-up-on-event
  [board {:keys [pin mode on-event] :as pin-config}]
  (println {:fn ::hook-up-on-event :pin-config pin-config})
  (when on-event
    (let [{:keys [topic-type reporting-fn]}
          (-> {:analog
               {:topic-type :analog-msg
                :reporting-fn #'fc/enable-analog-in-reporting}

               :input
               {:topic-type :digital-msg
                :reporting-fn #'fc/enable-digital-port-reporting}}
              (get mode))

          ch (a/chan)]
      (reporting-fn board pin true)
      (a/sub (fc/event-publisher board) [topic-type pin] ch)
      (a/go-loop []
        (when-let [event (<! ch)] (on-event event))
        (recur)))))

(defn setup-pin
  [board {:keys [pin mode value] :as pin-config}]

  (fc/set-pin-mode board pin mode)

  (cond
    (= :analog mode) (hook-up-on-event board pin-config)
    (= :input mode)  (hook-up-on-event board pin-config)
    (= :output mode) (fc/set-digital board pin value)
    (= :pwm mode)    (fc/set-analog board pin value)))

(defn init!
  [ctx]
  (-> ctx
      (swap! assoc :board (fc/open-serial-board :auto-detect))))

(defn setup
  [board]
  (doseq [[k v] pins]
    (tap> {:message "setting up pin" :pin k :config v})
    (setup-pin board v)))

(comment
  (init! ctx)
  (setup (:board @ctx))
  (reset-board)
  (fc/close! (:board @ctx)))

