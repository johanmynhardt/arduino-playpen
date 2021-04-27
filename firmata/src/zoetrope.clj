(ns zoetrope
  (:require [clojure.core.async :as a :refer [<!]]
            [firmata.core :as fc]))

(defonce ctx
  (atom {:board nil
         :motor-speed 0}))

(defn reset-board
  []
  (fc/reset-board (:board @ctx))
  (a/unsub-all (fc/event-publisher (:board @ctx))))

(def hl-switch
  {:high :low
   :low :high})

(def pins
  {:control1 {:pin 2 :mode :output :value :low :hbridge/pin 7}
   :control2 {:pin 3 :mode :output :value :low :hbridge/pin 2}
   :enable   {:pin 9 :mode :pwm :value 0 :hbridge/pin 1}

   :direction {:pin 4 :mode :input :value :low
               :on-event
               (fn [{:keys [value] :as event}]
                 #_(tap> (assoc event
                              :message  "direction toggle"
                              :control1 {:pin (-> pins :control1 :pin) :value value}
                              :control2 {:pin (-> pins :control2 :pin) :value (hl-switch value)}))

                 (fc/set-digital (:board @ctx) (-> pins :control1 :pin) value)
                 (fc/set-digital (:board @ctx) (-> pins :control2 :pin) (hl-switch value)))}

   :onOff {:pin 5 :mode :input :value 0
           :on-event
           (fn [{:keys [value] :as event}]
             (let [ms          (:motor-speed @ctx)
                   motor-speed (if (and (pos? ms) (= value :high)) (int (/ ms 4)) 0)]
               #_(tap> (-> event
                         (assoc :message "setting motor speed")
                         (assoc :enable {:pin (-> pins :enable :pin) :value motor-speed})))

               (fc/set-analog (:board @ctx) (-> pins :enable :pin) motor-speed)))}

   :pot {:pin 0 :mode :analog :value 0
         :on-event
         (fn [{:keys [value] :as event}]
           #_(println "pot changed: " event)
           (swap! ctx assoc :motor-speed value))}})

(defn hook-up-on-event
  [board {:keys [pin mode on-event] :as pin-config}]
  (when on-event
    (let [{:keys [topic-type reporting-fn]}
          (-> {:analog
               {:topic-type :analog-msg
                :reporting-fn #'fc/enable-analog-in-reporting}

               :digital
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
    (println "setting up pin: " k)
    (setup-pin board v)))

(comment
  (init! ctx)
  (setup (:board @ctx))
  (reset-board)
  (fc/close! (:board @ctx)))

