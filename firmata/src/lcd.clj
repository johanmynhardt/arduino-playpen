(ns lcd
  (:require [firmata.core :as f]
            [firmata.async :as fa]
            [partsbox.lcd :as lcd]
            [util]
            [clojure.core.async :as a :refer [<! >! <!! chan go-loop alts! timeout]]
            [clojure.string :as str]))



(comment

  (+ 2 2)

  (let [board (f/open-serial-board :auto-detect)
        lcd (-> (lcd/create-lcd board 12 11 5 4 3 2)
                (lcd/clear)
                (lcd/begin 16 2))]

    #_(Thread/sleep 500)
    (-> lcd
        #_(lcd/clear)
        (lcd/print "hello world"))

    (Thread/sleep 10000)
    (f/close! board)
    (println ";; closing"))


  (def board (f/open-serial-board :auto-detect))
  (def lcd (-> board
               (lcd/create-lcd 12 11 5 4 3 2)
               (lcd/clear)
               (lcd/begin 16 2)
               (lcd/print "Hello! (^^,)")
               (lcd/no-blink)))

  (f/close! board)

  ;;; Tilt switch

  (f/enable-digital-port-reporting board 6 true)

  (println "x")

  (let [#_#_board (f/open-serial-board :auto-detect)
        _
        (do (f/set-pin-mode board 6 :input)
            (f/enable-digital-port-reporting board 6 true))

        lcd (-> board
                (lcd/create-lcd 12 11 5 4 3 2)
                (lcd/clear)
                (lcd/begin 16 2)
                (lcd/print "Hello! (^^,)")
                (lcd/no-blink))

        tc (chan)
        tsub (-> (f/event-publisher board)
                 (a/sub [:digital-msg 6] tc)
                 (util/debounce 100))]
    (lcd/clear lcd)
    (lcd/print lcd "Start tilting!")
    (let [gl
          (a/go-loop [x 0]
            #_(a/timeout 100)
            (when-let [event (<! tsub)]
              (println event)
              (println "---" x)
              (condp = (:value event)
                :low (do
                        (lcd/clear lcd)
                        (lcd/print lcd "TILTED"))
                :high (do
                       (lcd/clear lcd)
                       (lcd/print lcd "RESET"))))
            (recur (inc x)))]

      (Thread/sleep 30000)
      (a/unsub (f/event-publisher board) [:digital-msg 6] tc)

      (a/close! gl))
    #_(f/close! board))

  (f/query-pin-state board 6)

  ;;; end tilt switch

  (-> lcd
    (lcd/clear)
    (lcd/print "0123456789abc")
    (lcd/display))
  (lcd/set-cursor lcd 0 0)

  (lcd/autoscroll lcd) ;; doesn't work?
  (lcd/no-cursor lcd) ;; doesn't work
  (lcd/no-blink lcd) ;; doesn't work

  (-> lcd
      (lcd/clear)
      (lcd/no-cursor)
      (lcd/print "hello"))

  (lcd/clear lcd)
  (lcd/begin lcd 16 2)
  (lcd/set-cursor lcd 0 0)
  (do (lcd/clear lcd)
      (lcd/print lcd (str (.format (java.text.SimpleDateFormat. "yyyy HH:mm:ss.SS") (java.util.Date.)))))

  (->> (partition-all 16 (seq "hello world, this is not sparta"))
       (map #(str/join "" %))
       )

  (lcd/set-cursor lcd 0 2)


  (lcd/home lcd)
  (lcd/set-cursor lcd 1 1)
  (lcd/print lcd "3")
  (lcd/println lcd "hello")
  (lcd/scroll-display-left lcd)
  (lcd/print lcd (str/join "" (interleave (range 16) (range 16))))

  (do (lcd/clear lcd)
      (lcd/println lcd "hello")
      (lcd/print lcd "world"))

  ;; It appears that the line/chars setup is incorrectly initialized.
  ;; But this session actually worked. yay.

  )

