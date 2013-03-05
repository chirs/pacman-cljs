(ns pacman.core
  (:require [pacman.constants :as const]
            [pacman.ghost :as ghost]
            [pacman.user :as user]
            [pacman.audio :as audio]
            [pacman.gamemap :as gamemap]
            [pacman.helpers :as helper]
            [pacman.state :as state]))
;; App
(defn get-tick []
  (:tick @state/game-state))

(defn draw-score 
  [text position]
  (helper/update-game :ctx (.fillStyle (:ctx @state/game-state) "#FFFFFF")))

(defn dialog [text ctx]
  (set! (. ctx  -fillStyle) (str "#FFFFFF"))
  ;; fill font!
  (let [dialog-width (.-width (.measureText ctx text))
        map-width (alength (aget const/game-map 0))
        map-height (alength const/game-map)
        x (/ (- dialog-width (* map-width (:block-size @state/game-state))) 2)]
    (.fillText ctx text x (+ (* map-height 10) 8))))

(defn sound-disabled 
  "Local storage dependent."
  [])

(defn start-level 
  "Start new level. "
  [])

(defn start-new-game [])

(defn key-down [e])

(defn lose-life [])

(defn set-state [n-state])

(defn collided [user ghost])

(defn draw-footer [])

(defn redraw-block [pos])

(defn main-draw [])

(defn main-loop []

  (if-not (= (:state @state/game-state) (:pause const/game-const)) 
    (swap! state/game-state update-in [:tick] (fnil inc 0)))

  (gamemap/draw-pills (:ctx @state/game-state))

  ;;More!
)

(defn eaten-pill [])

(defn completed-level [])

(defn key-press [e])

(defn loaded []
  (dialog "Press N to Start" (:ctx @state/game-state))
  (.setInterval js/window (main-loop) (/ 1000 const/FPS)))

(defn load [my-vec callback]
  (callback) 
  (if (= (count my-vec) 0) 
    ;; uuh...this should work?
    (callback)
    #_(do
      (let [x (last my-vec)]
        (audio/load (nth x 0) (nth x 1)) #(load my-vec callback)))))

(defn init [wrapper root]
  (let [canvas (.createElement js/document "canvas")
        block-size (/ (.-offsetWidth wrapper) 19)]
    (.setAttribute canvas "width" (str (* block-size 19) "px"))
    (.setAttribute canvas "height" (str (+ (* block-size 22) 30) "px"))
    (.appendChild wrapper canvas)

    ;; Set mutable vars
    (swap! state/game-state assoc-in [:ctx] (.getContext canvas "2d"))
    (swap! gamemap/map-state assoc-in [:block-size] block-size)
    (swap! state/game-state update-in [:audio] conj {:sound-disabled true})
    (swap! state/game-state update-in [:user] conj {:completed-level completed-level
                                                    :eaten-pill eaten-pill})
    (gamemap/draw (:ctx @state/game-state)) 

    ;; (dialog "Loading..." (:ctx @state/game-state))
    (let [extension (str "mp3")
          audio-files [{:start (str root "audio/opening_song." extension)}
                       {:die (str root  "audio/die." extension)}
                       {:eatghost (str root "audio/eatghost." extension)}
                       {:eatpill (str root "audio/eatpill." extension)}
                       {:eating (str root  "audio/eating.short." extension)}
                       {:eating2 (str root "audio/eating.short." extension)}]]
      (load audio-files loaded))))

;; Init!
;; window.setTimeout(function () { PACMAN.init(el, "./"); }, 0);
(def elem (helper/get-element-by-id "pacman"))
(.setTimeout js/window (fn [x] (init elem "./")) 0)
;(set! (.-onload js/window) (init elem "./"))