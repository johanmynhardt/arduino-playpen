(ns util)

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
