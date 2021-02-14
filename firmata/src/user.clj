(ns user
  (:require [firmata.core :as f]
            [firmata.async :as f.a]
            [clojure.core.async :refer [<!! <! go-loop] :as a]))

(defn raw->pwm [value]
  (-> value
      (* (/ 255 1023))
      int))

#_(def board (f/open-serial-board  :auto-detect #_#_:baud-rate 9600))
#_(f/reset-board board)
#_(f/close! board)

#_(f/set-pin-mode)
#_(-> board
      (f/enable-analog-in-reporting 0 true))

#_(f/enable-analog-in-reporting board 0 true)

#_(let [_ (f/enable-analog-in-reporting board 0 true)
        ch    (f/event-channel board)
        event (<!! ch)]
    event)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Potentiometer
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_(def analog-r
    (let [_ (f/enable-analog-in-reporting board 0 true)
          _ (f/set-pin-mode board 3 :pwm)
          ch    (f/event-channel board)
          last-v (atom 0)]
      (future
        (while true
          (let [{:keys [value] :as msg} (<!! ch)
                old-v @last-v]
            (when (and (= :analog-msg (:type msg))
                       (= 0 (:pin msg))
                       (not= old-v (:value msg)))
              (println msg)
              (f/set-analog board 3 (raw->pwm value))
              (reset! last-v (:value msg))
              (f/set-digital
               board 5
               (if (= 1023 value)
                 :high
                 :low))))))))

#_(future-cancel analog-r)

#_(let [_ (f/enable-analog-in-reporting board 0 true)
        _ (f/set-pin-mode board 3 :pwm)
        sub-ch (a/chan)
        lastv (atom 0)]
    (swap! brd assoc :analog/sub-ch-0 sub-ch)
    (a/sub (f/event-publisher board) [:analog-msg 0] sub-ch)
    (a/go-loop []
      (when-let [{:keys [value] :as event} (<! sub-ch)]
        (when (not= value @lastv)
          #_(println event)
          (f/set-analog board 3 (raw->pwm value))
          (reset! lastv value)
          (when (= value 1023)
            (a/go
              (letter-sequence board 4 [:S :O :S])))))
      (recur)))

(defonce brd (atom {}))

(defn stop-sub [k]
  (let [sub (get @brd k)]
    (when sub
      (a/close! sub)
      (swap! brd assoc k nil))))

(stop-sub :analog/sub-ch-0)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Temperature
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



#_(def temp
  (let [_ (f/enable-analog-in-reporting board 1 true)
        ch (f/event-channel board)
        alast (atom 0)]
    (future
      (while true
        (let [{:keys [type pin value] :as event} (<!! ch)
              last-v @alast]
          (when (and (= :analog-msg type)
                     (= 1 pin)
                     (not= last-v value))
            (reset! alast value)
            (println "tempval: " value)))))))

#_(future-cancel temp)

#_(f/set-pin-mode board 3 :output)
#_(f/set-digital board 3 :low)
#_(f/set-analog board 3 (int 254.0))

#_(f/reset-board board)

#_(def analog-r2
    (let [_ (f/enable-analog-in-reporting board 0 true)
          ch (f.a/analog-event-chan board 0)]
      (future
        (a/go-loop [e (<!! ch)]
          (println "e:" e)
          (recur (<!! ch))))))

#_(future-cancel analog-r2)

#_(println "x")


(def pulse-rate
  {:s 100
   :l 400})

(def letters
  {:O [:l :l :l]
   :S [:s :s :s]})

(defn pulse [board pin dur]
  (f/set-digital board pin :high)
  (Thread/sleep dur)
  (f/set-digital board pin :low))

(defn letter [board pin col]
  (doseq [x col]
    (pulse board pin (x pulse-rate))
    (Thread/sleep 100)))

(defn letter-sequence [board pin col]
  (doseq [l col]
    (letter board pin (l letters))
    (Thread/sleep 200)))

(comment

  (def puls
    (future
      (let [r (range 0 255 5)
            r' (reverse r)]
        (while true
          (doseq [[a b] (partition 2 (interleave r r'))]
            (f/set-analog board 3 a)
            (f/set-analog board 5 b)
            (Thread/sleep 10))
          (Thread/sleep 500)))))

  (future-cancel puls)

  (do ;; all on
    (f/set-digital board 3 :high)
    (f/set-digital board 4 :high)
    (f/set-digital board 5 :high))

  (def sos-button
    (future
      (-> board
          (f/set-pin-mode 2 :input)
          (f/enable-digital-port-reporting 2 true))
      (let [ch (f/event-channel board)]
        (while true
          (let [{:keys [type pin value]} (<!! ch)]
            (when (and (= :digital-msg type)
                       (= 2 pin)
                       (= :high value))
              (letter-sequence board 4 [:S :O :S])))))))

  (future-cancel sos-button)
  ,)

(comment
  (letter-sequence board 13 [:S :O :S])

  (def sos (future
             (while true
               (letter-sequence board 3 [:S :O :S])
               (Thread/sleep 100))))

  (future-cancel sos))
