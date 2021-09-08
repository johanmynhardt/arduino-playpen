(ns lcd
  (:require [firmata.core :as f]
            [partsbox.lcd :as lcd]
            [clojure.string :as str]))

(comment


  (let [board (f/open-serial-board :auto-detect)
        lcd (lcd/create-lcd board 12 11 5 4 3 2)]

    (-> lcd
        (lcd/clear)
        (lcd/print "hello world"))

    (f/close! board))


  (def board (f/open-serial-board :auto-detect))
  (def lcd (lcd/create-lcd board 12 11 5 4 3 2))

  (lcd/clear lcd)
  (lcd/begin lcd 16 2)
  (lcd/set-cursor lcd 0 0)
  (do (lcd/clear lcd)
      ;(lcd/autoscroll lcd)
      (lcd/print lcd "hello\nworld" #_(str (.format (java.text.SimpleDateFormat. "yyyy HH:mm:ss.SS") (java.util.Date.)))))

  (do (lcd/clear lcd)
      (lcd/println lcd "hello")
      (lcd/print lcd "world"))

  (lcd/home lcd)
  (lcd/print lcd (str/join "" (interleave (range 16) (range 16))))

  ;; It appears that the line/chars setup is incorrectly initialized.
  ;; But this session actually worked. yay.

  )

