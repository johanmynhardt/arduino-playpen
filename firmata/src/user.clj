(ns user
  (:require [firmata.core :as f]
            [clojure.core.async :refer [<!!]]))

;(require '[firmata.core :refer [all]])

(def board (f/open-serial-board  :auto-detect #_#_:baud-rate 9600))

@(:board-state board)

#_(f/set-digital board 13 1)

#_(f/close! board)

#_(let [ch (f/event-channel board)
      _ (f/query-firmware board)
      event (<!! ch)]
  (:name event))
