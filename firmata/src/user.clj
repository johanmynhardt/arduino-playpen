(ns user
  (:require [firmata.core :as f]
            [clojure.core.async :refer [<!!]]))

;(require '[firmata.core :refer [all]])

#_(def board (f/open-serial-board  :auto-detect #_#_:baud-rate 9600))

#_(f/close! board)



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
    (do (-> board
           (f/set-pin-mode 2 :input)
           (f/enable-digital-port-reporting 2 true))
        (future
          (while true
            (let [ch    (f/event-channel board)
                  event (<!! ch)]
              (when (= :high (:value event))
                (letter-sequence board 4 [:S :O :S])))
            (Thread/sleep 100)))))

  (future-cancel sos-button)
  ,)

(comment
  (letter-sequence board 4 [:S :O :S])

  (def sos (future
             (while true
               (letter-sequence board 4 [:S :O :S])
               (Thread/sleep 100))))

  (future-cancel sos))
