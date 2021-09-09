(ns lcd
  (:require [firmata.core :as f]
            [partsbox.lcd :as lcd]
            [clojure.string :as str]))

(comment


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
               (lcd/print "Init done! (^^,)")))

  (f/close! board)

  (lcd/print lcd "0123456789abc")
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
  (lcd/set-cursor lcd 0 1)
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

