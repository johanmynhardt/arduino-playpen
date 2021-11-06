(ns util
  (:require [clojure.core.async :refer [chan go-loop timeout alts! >! ]]
            [clojure.string :as str]))

(defn raw->pwm [value]
  (-> value
      (* (/ 255 1023))
      int))

(defn raw->tmp [value]
  (-> value
      (/ 1023)
      (* 5)
      (- 0.5)
      (* 100)
      double))

(defn translate
  [value {in-min :in/min in-max :in/max
          out-min :out/min out-max :out/max}]

  (let [frac (double (/ value (- in-max in-min)))
        in-range (- in-max in-min)
        out-range (- out-max out-min)
        out-frac (* frac out-range)]
    {:value value
     :frac frac
     :in-range in-range
     :out-range out-range
     :out-frac out-frac
     :out-val (+ out-min out-frac)}))

#_(translate 0 {:in/min 0
                :in/max 1023
                :out/min 0
                :out/max 180})

(defn debounce
  "Debouncer from: https://gist.github.com/xfsnowind/e15cc2e6da74df81f129
  In the case of Firmata, this worked well when wrapping a subscription."

  ([source msecs]
   (debounce (chan) source msecs))
  ([c source msecs]
   (go-loop [state ::init
             last-one nil
             cs [source]]
     (let [[_ threshold] cs
           [v sc] (alts! cs)]
       (condp = sc
         source
         (condp = state
           ::init (recur ::debouncing v (conj cs (timeout msecs)))
           ::debouncing (recur state v (conj (pop cs) (timeout msecs))))

         threshold
         (cond last-one
               (do (>! c last-one)
                   (recur ::init nil (pop cs)))

               :else
               (recur ::init last-one (pop cs))))))
   c))
