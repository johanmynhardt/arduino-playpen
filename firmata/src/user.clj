(ns user
  (:require [clojure.core.async :as a :refer [<! <!!]]
            [clojure.set :as set]
            [firmata.core :as f]
            [util :refer [raw->pwm raw->tmp]]))

(defonce ctx (atom {}))

(defn init []
  (swap! ctx assoc :board (f/open-serial-board :auto-detect)))
#_(init)

(defn reset []
  (f/reset-board (:board @ctx)))

(defn close []
  (f/close! (:board @ctx)))

(defn stop-topic [ctx k]
  (let [{:keys [topics board]} @ctx
        {:keys [topic chan]} (get topics k)]
    (a/unsub (f/event-publisher board) topic chan)
    (swap! ctx assoc-in [:topics k :chan] nil)))

(comment
  #_(rvl/clear-output)
  (init)
  (reset)
  (close))

#_(ns-interns *ns*)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; relay
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (f/set-pin-mode (:board @ctx) 6 :output)

  (f/set-digital (:board @ctx) 6 :high)

  (future (doseq [x (range 5)]
            (let [rsleep (rand-nth (range 500 1000 5))]
              (f/set-digital (:board @ctx) 6 :high)
              (Thread/sleep rsleep)
              (f/set-digital (:board @ctx) 6 :low)
              (Thread/sleep rsleep))))

  (+ 1 3)
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Motor stuff
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment 
  (f/set-pin-mode (:board @ctx) 2 :input)
  (f/set-pin-mode (:board @ctx) 9 :output)

  (def button-ch (a/chan))

  (a/sub (f/event-publisher (:board @ctx)) [:digital-msg 2] button-ch)

  (f/enable-digital-port-reporting (:board @ctx) 2 true)

  (a/go-loop []
    (when-let [event (<! button-ch)]
      ;(println "button: " event)
      (if (= :low (:value event)) (f/set-digital (:board @ctx) 9 :low)
          (doseq [_ (range 20)]
            (f/set-digital (:board @ctx) 9 :high)
            (Thread/sleep 3)
            (f/set-digital (:board @ctx) 9 :low)
            (Thread/sleep 50))))
    (recur))



  (a/unsub (f/event-publisher (:board @ctx)) [:digital-msg 2] button-ch)


  

  (a/timeout 500))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SOS stuff
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Potentiometer
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-pot [ctx]
  (let [board (:board @ctx)
        sub-ch (a/chan)
        lastv (atom 0)
        topic [:analog-msg 0]]

    (f/enable-analog-in-reporting board 0 true)
    (f/set-pin-mode board 3 :pwm)

    (a/sub (f/event-publisher board) topic sub-ch)

    (a/go-loop []
      (when-let [{:keys [value] :as event} (<! sub-ch)]
        (when (not= value @lastv)
          #_(println event)
          (f/set-analog board 3 (raw->pwm value))
          (reset! lastv value)
          (swap! ctx assoc-in [:sensor :pot] value)
          (when (= value 1023)
            (a/go
              (letter-sequence board 4 [:S :O :S])))))
      (recur))
    (swap! ctx assoc-in [:topics :pot]
           {:topic topic
            :chan sub-ch})))
#_(start-pot ctx)
#_(stop-topic ctx :pot)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Temperature
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn start-temp [ctx]
  (when (-> @ctx :topics :temp :chan nil?)
    (let [pin 1
          board (:board @ctx)
          sub-ch (a/chan)
          last-v (atom 0)
          topic [:analog-msg pin]]

      (f/enable-analog-in-reporting board pin true)
      (a/sub (f/event-publisher board) topic sub-ch)

      (a/go-loop [vbuffer []]
        (let [{:keys [value] :as event} (<! sub-ch)]
          (cond (<= 50 (count vbuffer))
                (let [avg
                      (-> (reduce + vbuffer)
                          (/ (count vbuffer))
                          raw->tmp)]
                  (swap! ctx assoc-in [:sensor :temp] avg)
                  (recur []))

                (and event (not= value @last-v))
                (recur (conj vbuffer value))

                :else (recur vbuffer))))

      (swap! ctx assoc-in [:topics :temp]
             {:topic topic
              :chan sub-ch}))))

#_(start-temp ctx)
#_(-> @ctx :sensor)
#_(stop-topic ctx :temp)

#_(add-watch ctx :temp-watch
             (fn [k r o n]
               (let [ot (get-in o [:sensor])
                     nt (get-in n [:sensor])]
                 (println nt))))

#_(remove-watch ctx :temp-watch)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Phototransistors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-light-detection [ctx pin]
  (let [topic-key (keyword (str "lightsensor-" pin))]
    (when (-> @ctx :topics topic-key :chan nil?)
      (let [board (:board @ctx)
            sub-ch (a/chan)
            last-v (atom 0)
            topic [:analog-msg pin]]

        (f/enable-analog-in-reporting board pin true)
        (a/sub (f/event-publisher board) topic sub-ch)

        (a/go-loop []
          (let [{:keys [value] :as event} (<! sub-ch)]
            (swap! ctx assoc-in [:sensor topic-key] value)
            #_(println (format "%s:%s" pin value))
            #_(println pin ":"
                     (str/join ""
                               (take (-> value
                                         (/ 10)
                                         int)
                                     (repeatedly (constantly "*")))))
            (recur)))

        (swap! ctx assoc-in [:topics topic-key]
               {:topic topic
                :chan sub-ch})))))



#_(start-light-detection ctx 1)
#_(->> (range 3)
       (map (partial start-light-detection ctx)))

#_(stop-topic ctx :lightsensor-0)
#_(->> (range 3)
       (map #(keyword (str "lightsensor-" %)))
       (map (partial stop-topic ctx)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; servo
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (f/set-pin-mode (:board @ctx) 9 :servo)
  (f/set-analog (:board @ctx) 9 0)


  (f/set-pin-mode (:board @ctx) 0 :input)

  (f/enable-analog-in-reporting (:board @ctx) 0 true)

  (def sub-ch (a/chan))

  (a/unsub (f/event-publisher (:board @ctx)) [:analog-msg 0] sub-ch)

  (a/sub (f/event-publisher (:board @ctx)) [:analog-msg 0] sub-ch)

  (def pot-future
    (future
      (a/go-loop []
        (when-let [event (<! sub-ch)]
          (let [{:keys [out-val]} (-> (:value event)
                                      (util/translate {:in/min 0 :in/max 1023 :out/min 0 :out/max 180}))]
            ;(println event ":" out-val)
            (f/set-analog (:board @ctx) 9 (int out-val))))
        (Thread/sleep 10)
        (recur))))

  (future-cancel pot-future)


  (let [{:keys [board]} @ctx]
    (f/set-analog board 9 90)
    (Thread/sleep 2000)
    (f/set-analog board 9 0)
    (Thread/sleep 1000)
    (f/set-analog board 9 180)
    (Thread/sleep 1000)
    (doseq [deg (shuffle (range 0 180 5))]
      (f/set-analog board 9 deg)
      (Thread/sleep 200))
    (Thread/sleep 500)
    (f/set-analog board 9 90))

  (/ 180 5)

  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def lights
  (future
    (while true
      (println (-> @ctx :sensor))
      (Thread/sleep 200))))

(future-cancel lights)

(comment

  (def rgbled-out
    (future
      (while true
        (->> (set/rename-keys
              (->> (seq (-> @ctx :sensor))
                   (map (fn [[k v]] [k (raw->pwm v)]))
                   (into {}))
              {:lightsensor-0 9
               :lightsensor-1 10
               :lightsensor-2 11})
             seq
             (map (fn [[pin val]]
                    #_(print ".")
                    #_(println pin ":" val)
                    (f/set-analog (:board @ctx) pin val)))
             doall)
        (Thread/sleep 10))))

  (future-cancel rgbled-out)

  (->> [9 10 11]
       (map #(f/set-pin-mode (:board @ctx) % :pwm)))

  (f/set-analog (:board @ctx) 9 0)
  (f/set-analog (:board @ctx) 10 0)
  (f/set-analog (:board @ctx) 11 0)
  )


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
      (let [{:keys [board]} @ctx
            ch (f/event-channel board)]
        (-> board
            (f/set-pin-mode 2 :input)
            (f/enable-digital-port-reporting 2 true))

        (while true
          (let [{:keys [type pin value]} (<!! ch)]
            (when (and (= :digital-msg type)
                       (= 2 pin)
                       (= :high value))
              (letter-sequence board 5 [:S :O :S])))))))

  (future-cancel sos-button)
  ,)

(comment
  (letter-sequence board 13 [:S :O :S])

  (def sos (future
             (while true
               (letter-sequence board 3 [:S :O :S])
               (Thread/sleep 100))))

  (future-cancel sos))
