(ns lcd-mosfet
  (:require [firmata.core :as f]
            [clojure.string :as str]))

(def board (f/open-serial-board :auto-detect))

(def colours
  {:red 3
   :blue 5
   :green 6})

(defn init-strip-lights [board]
  (doseq [[_ pin] colours]
    (f/set-pin-mode board pin :pwm)
    (f/set-analog board pin 0)))

(init-strip-lights board)

(defn to-five-digits [v]
  (-> (format "%020.5f" v)
      (str/replace #"\," ".")
      (Double/parseDouble)))

(defn fx-sin [deg]
  (-> deg
      (Math/toRadians)
      (Math/sin)
      (to-five-digits)))

(defn fx-sin-inverse [deg]
  (-> deg
      (Math/toRadians)
      (+ Math/PI)
      (Math/sin)
      (+ 1)
      (to-five-digits)))

(defn fx-square [deg]
  (Math/round (Math/sin (Math/toRadians deg))))

(defn pulse
  [board & [{:keys [times duration fx clrs]
             :or {times 1
                  fx #'fx-sin
                  duration 500
                  clrs [:red :green :blue]}}]]

  (let [vals (->> (range 0 185 5)
                  (map #(-> (fx %) (* 255)))
                  (repeat times)
                  (flatten))
        sleep (/ duration (count vals))]
    (doseq [x vals]
      (doseq [clr clrs]
        (f/set-analog board (get colours clr) (int x)))
      (Thread/sleep sleep))))

(defn white-on [board]
  (doseq [pin (vals colours)]
    (f/set-analog board pin 255)))

(defn white-off [board]
  (doseq [pin (vals colours)]
    (f/set-analog board pin 0)))

(defn fx-abs-sin-x+pi-div
  [deg div]
  (-> deg
      (Math/toRadians)
      (+ (/ Math/PI div))
      (Math/sin)
      (Math/abs)
      (to-five-digits)))

(defn ^{:experimental true
        :doc "This is still a WIP trying to find the best function to display different colours."}
  cycle-colours
  [board]
  (doseq [vs
          (->> (range 0 3650 1)
               (map (fn [deg]
                      [(fx-abs-sin-x+pi-div deg 4)
                       (fx-abs-sin-x+pi-div deg 1)
                       (fx-abs-sin-x+pi-div deg 2)])))]
    #_(println (map #(-> % (* 255) (int)) vs))
    (doseq [[pin v] (zipmap (vals colours) vs)]
      (f/set-analog board pin (-> v (* 255) (int))))
    (Thread/sleep 3)))

(comment
  (do
    (Thread/sleep 1000)
    (cycle-colours board))
  (white-on board)
  (white-off board)

  (pulse board
         {:clrs [:green :blue :red]
          :fx #'fx-sin
          :times 1
          :duration 2000})

  (doseq [clr (into (keys colours) (repeat 2 (keys colours)))]
    (pulse board
           {:clrs (if (seq? clr) clr [clr])
            :fx #'fx-sin
            :times 1
            :duration 500}))

  #_())

(defn gen-pattern
  [pin & [{:keys [fx duration times]
           :or {fx #'fx-sin
                duration 1000
                times 1}}]]
  (let [vals
        (->> (range 0 185 5)
             (map #(-> (fx %) (* 255)))
             (repeat times)
             (flatten)
             (map (fn [v] [pin (int v)])))

        sleep (/ duration (count vals))

        pattern
        (->> [:sleep sleep]
             (constantly)
             (repeatedly)
             (interleave vals)
             (partition 2))]
    pattern))

(defn flash-pattern [board pattern-coll]
  (doseq [steps pattern-coll]
    (let [to-write (remove (comp #(= % :sleep) first) steps)
          sleep (filter (comp #(= % :sleep) first)  steps)]
      (doseq [[pin v] to-write] (f/set-analog board pin v))
      (when sleep
        (doseq [[_ s] sleep] (Thread/sleep s))))))

(comment
  (flash-pattern
   board
   (gen-pattern 3
                {:fx #(Math/round (Math/sin (Math/toRadians %)))
                 :times 5}))

  (do
    (flash-pattern board (gen-pattern 5 {:fxx #'fx-sin-inverse}))
    (flash-pattern board (gen-pattern 3 {:fxx #'fx-sin})))

  (doseq [x (range 5)]
    (flash-pattern
     board
     (map
      (comp (partial sort-by (comp keyword? first))
            (partial distinct)
            (partial partition 2)
            flatten)
      (partition 2
                 (interleave
                  (gen-pattern 5 {})
                  (gen-pattern 3 {:fx #'fx-sin-inverse}))))))

  #_())
