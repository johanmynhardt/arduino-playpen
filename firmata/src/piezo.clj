(ns piezo
  (:require [clojure.core.async :as a :refer [<!]]
            [firmata.core :as fc]))

(defonce ctx
  (atom {:board nil}))

(defn init!
  [ctx]
  (-> ctx
      (swap! assoc :board (fc/open-serial-board :auto-detect))))

(def pins
  {:buzzer {:pin 6 :mode :pwm :value 0}})

(defn setup-pin
  [board {:keys [pin mode value] :as pin-config}]

  (fc/set-pin-mode board pin mode)

  (cond
    ;(= :analog mode) (hook-up-on-event board pin-config)
    ;(= :input mode)  (hook-up-on-event board pin-config)
    (= :output mode) (fc/set-digital board pin value)
    (= :pwm mode)    (fc/set-analog board pin value)))

(defn setup
  [board]
  (doseq [[k v] pins]
    (setup-pin board v)))

(comment
  (init! ctx)
  (setup (:board @ctx))

  (fc/set-pin-mode (:board @ctx) 6 :output)

  (doseq [x (take 1000 (cycle [:high :low]))]
    (fc/set-digital (:board @ctx) 6 x)
    (Thread/sleep 1))

  (fc/set-digital (:board @ctx) 6 :low)

  (fc/set-pin-mode (:board @ctx) 6 :pwm)

  (doseq [x (range 100 240)]
    (fc/set-analog (:board @ctx) 6 x)
    (Thread/sleep 50)
    (.sleep (java.util.concurrent.TimeUnit/MICROSECONDS) 20)
    (fc/set-analog (:board @ctx) 6 0)
    ;(Thread/sleep 10)
    (.sleep (java.util.concurrent.TimeUnit/MICROSECONDS) 20)
    )

  (fc/set-analog (:board @ctx) 6 0)

  (.sleep (java.util.concurrent.TimeUnit/MICROSECONDS) 200)

  

  )
