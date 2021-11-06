(ns piezo-input 
  (:require [firmata.core :as f]
            [clojure.core.async :refer [chan sub unsub go-loop <!]]
            [clojure.string :as str]))

(comment

  (def board (f/open-serial-board :auto-detect))

  (f/close! board)

  ;(f/enable-analog-port-reporting board 0 true)
  (f/enable-analog-in-reporting board 0 true)

  (def piezo-ch (chan))

  (sub (f/event-publisher board) [:analog-msg 0] piezo-ch)

  (unsub (f/event-publisher board) [:analog-msg 0] @mfut)


  (defn loudness-line [v]
    (let [loudness (-> v
                    (/ 1023)
                    (* 80)
                    (int))]
      (when (pos? loudness)
        (println (str/join (repeat loudness "="))))))

  (def mfut
    (future
      (go-loop []
        (when-let [event (<! piezo-ch)]
          (when (pos? (:value event))
            (loudness-line (:value event))))
        (recur))))

  (let [v 250]
    (println (str/join (repeat (-> v (/ 1023)
                                   (* 80)
                                   (int)) "="))))

  (clojure.core.async/close! @mfut)
  (future-cancelled? mfut)

(println "x")
  (shutdown-agents)

  (unsub (f/event-publisher board) [:analog-msg 0] piezo-ch)
  (clojure.core.async/close! piezo-ch)

  (future-cancel mfut)





  )
