(ns pacman.game
;; (:require-macros [pacman.macros :as m])
  (:require [pacman.constants :as const]
            [pacman.helpers :as helper]
            [goog.dom :as dom]
            [clojure.browser.repl :as repl]))

(repl/connect "http://localhost:9000/repl")

;; =============================================================================
;; Definitions

(def canvas (.getElementById js/document "canvas"))
(def ctx (.getContext canvas "2d"))
(def controls { (:ARROW_LEFT  const/KEYS) :left
                (:ARROW_UP    const/KEYS) :up
                (:ARROW_RIGHT const/KEYS) :right
                (:ARROW_DOWN  const/KEYS) :down })

;; Game State
(defn make-ghost [color] 
  {:get-tick 0, 
   :eatable nil, 
   :color color, 
   :eaten nil, 
   :specs color, 
   :position nil, 
   :due nil, 
   :speed 2,
   :map nil, 
   :npos nil,
   :old-pos nil,
   :direction nil})

(def ghost-specs ["#00FFDE" "#FF0000" "#FFB8DE" "#FFB847"])

(def game-state
  {:phase :waiting
   :dialog nil
   :countdown 4
   :dying-state 3
   :user {:position nil
          :old-pos nil
          :direction nil
          :due :left
          :speed 2
          :lives 3
          :eaten 0
          :score 0
          :block nil}
   :map { :height nil
          :width nil
          :pill-size 0 
          :block-size nil

          :board const/game-map}
   :audio []
   :ghosts (mapv make-ghost ghost-specs)
   :ghost-specs ["#00FFDE" "#FF0000" "#FFB8DE" "#FFB847"]
   :level 0
   :tick 0
   :ghost-pos []
   :state-changed true
   :timer-start nil
   :last-time 0
   :ctx nil
   :timer nil
   :stored nil
   :n-score 0})

(defn get-tick []
  (:tick game-state))

;; =============================================================================
;; Draw game board 

(defn draw-dialog [{map :map dialog :dialog :as state}]
  (if dialog
    (do
      (set! (. ctx  -fillStyle) "#FFFF00")
      (set! (. ctx -font) "14px BDCartoonShoutRegular")
      (let [dialog-width (.-width (.measureText ctx dialog))
            map-width 19
            map-height 22
            x (/ (- (* map-width (:block-size map)) dialog-width) 2)]
        (.fillText ctx dialog x (+ (* map-height 10) 8))
        state)))
  state)

(defn draw-score 
  [{map :map :as state} text position]
  (set! (. ctx  -fillStyle) "#FFFFFF")
  (set! (. ctx -font) "12px BDCartoonShoutRegular")
  (.fillText ctx text (* 10 (:block-size map)) (* 10 (:block-size map)))
  state)

(declare draw-block draw-pills)

(defn draw-footer [{map :map user :user :as state}]
  (let [block-size (:block-size map)
        map-width (count (get const/game-map 0))
        map-height (count const/game-map)
        top-left (* map-height block-size)
        text-base (+ top-left 17)]
    (set! (. ctx -fillStyle) "#000000")
    (.fillRect ctx 0 top-left (* map-width block-size) 30)
    (set! (. ctx -fillStyle) "#FFFF00")
    (doseq [i (range (:lives user))]
      (set! (. ctx -fillStyle) "#FFFF00")
      (doto ctx
        (.beginPath)
        (.moveTo (+ 150 (* 25 i) (/ block-size 2)) 
                 (+ (+ top-left 1) (/ block-size 2)))
        (.arc (+ (+ 150 (* 25 i)) (/ block-size 2)) 
              (+ (+ top-left 1) (/ block-size 2)) 
              (/ block-size 2)
              (* (.-PI js/Math) 0.25)
              (* (.-PI js/Math) 1.75)
              false)
        (.fill)
        state))

    (set! (. ctx -font) "bold 16px sans-serif")
    (.fillText ctx "s" 10 text-base)
    
    (set! (. ctx -fillStyle) "#FFFF00")
    (set! (. ctx -font) "14px BDCartoonShoutRegular")
    
    (.fillText ctx (str "Score: " (:score user)) 30 text-base)
    (.fillText ctx (str "Level: " (:level state)) 260 text-base)
    state))

(declare draw-wall is-floor-space? move-pacman)

(defn draw-map [{map :map :as state}]
  (set! (. ctx  -fillStyle) "#000")
  (let [width  (:width map)
        height (:height map)
        size   (:block-size map)]
    (.fillRect ctx 0 0 (* width size) (* height size))
    (draw-wall map)
    (doseq [i (range height)] 
      (doseq [j (range width)]
        (draw-block state {:y i :x j} size)))
    state))

;; =============================================================================
;; Draw Pac-Man

(defn calc-angle [dir pos]
  (cond
    (and (= dir :right) (< (mod (:x pos) 10) 5)) {:start 0.25 :end 1.75 :direction false}
    (and (= dir :down)  (< (mod (:y pos) 10) 5)) {:start 0.75 :end 2.25 :direction false}
    (and (= dir :up)    (< (mod (:y pos) 10) 5)) {:start 1.25 :end 1.75 :direction true}     
    (and (= dir :left)  (< (mod (:x pos) 10) 5)) {:start 0.75 :end 1.25 :direction true}
    :else {:start 0 :end 2 :direction false}))

(defn redraw-block [{map :map :as state}]
  (let [{{pos :position} :user} state
        bs (:block-size map)
        fy (Math/floor (/ (:y pos) 10))
        fx (Math/floor (/ (:x pos) 10))
        cy (Math/ceil (/ (:y pos) 10))
        cx (Math/ceil (/ (:x pos) 10))]
    (draw-block state {:y fy :x fx} bs)
    (draw-block state {:y cy :x cx} bs)
    state))

(defn draw-pacman [{map :map user :user :as state}]
  (let [s        (:block-size map)
        position (:position user)
        angle    (calc-angle (:direction user) position)]
    (set! (. ctx  -fillStyle) "#FFFF00")
    (doto ctx
      (.beginPath)
      (.moveTo (+ (* (/ (:x position) 10) s) (/ s 2))
        (+ (* (/ (:y position) 10) s) (/ s 2)))
      (.arc (+ (* (/ (:x position) 10) s) (/ s 2))
        (+ (* (/ (:y position) 10) s) (/ s 2))
        (/ s 2)
        (* (.-PI js/Math) (:start angle))
        (* (.-PI js/Math) (:end angle))
        (:direction angle))
      (.fill))
    state))

(defn draw-dead [{map :map :as state} amount]

  (let [size (:block-size map)
        half (/ size 2)
        position (:position (:user state))]
    (if-not (>= amount 1)
      (set! (. ctx  -fillStyle) "#FFFF00")
      (doto ctx
        (.beginPath)
        (.moveTo (+ (* (/ (:x position) 10) size) half)
          (+ (* (/ (:y position) 10) size) half))

        (.arc (+ (* (/ (:x position) 10) size) half)
          (+ (* (/ (:y position) 10) size) half)
          half
          0
          (* (.-PI js/Math) 2 amount)
          true)
        (.fill)))
    state))

;; =============================================================================
;; Draw Ghosts

(declare seconds-ago get-color)

(defn draw-ghost [ghost {map :map user :user :as state}]
  (let [ position (:position ghost)
         bs (:block-size map)
         eatable (:eatable ghost)
         top (* (/ (:y position) 10) bs)
         left (* (/ (:x position) 10) bs)
         base (- (+ top bs) 3)
         tl (+ left bs)
         inc (/ bs 10)
         high (if (> (mod (:tick state) 10) 5) 3 -3)
         low (if (> (mod (:tick state) 10) 5) -3 3)
         direction (or (:direction ghost) :up)
         f (/ bs 12)
         offset {:right [f 0] :left [(- f) 0] :up [0 (- f)] :down [0 f]}]

    (set! (. ctx -fillStyle) (:color ghost))

    ;; Body
    (doto ctx
      (.beginPath)
      (.moveTo left base)
      (.quadraticCurveTo left top (+ left (/ bs 2)) top)
      (.quadraticCurveTo (+ left bs) top (+ left bs) base)
      ;;(.quadraticCurveTo (- tl (* inc 1)) (+ base high) (- (* inc 2) tl) base)
      ;;(.quadraticCurveTo (- tl (* inc 3)) (+ base low)  (- (* inc 4) tl) base)
      ;;(.quadraticCurveTo (- tl (* inc 5)) (+ base high) (- (* inc 6) tl) base)
      ;;(.quadraticCurveTo (- tl (* inc 7)) (+ base low)  (- (* inc 8) tl) base)
      ;;(.quadraticCurveTo (- tl (* inc 9)) (+ base low)  (- (* inc 8) tl) base)
      (.closePath)
      (.fill)
      (.beginPath))

    (set! (. ctx -fillStyle) "#FFF")

   ;; Whites of eyes
    (doto ctx
      (.arc (+ left 6) (+ top 6) (/ bs 6) 0 300 false)
      (.arc (- (+ left bs) 6) (+ top 6) (/ bs 6) 0 300 false)
      (.closePath)
      (.fill))

    ;; Pupils
    (.beginPath ctx)
    (set! (. ctx -fillStyle) "#000")
    (doto ctx
      (.arc (+ left 6 (nth (direction offset) 0)) 
            (+ left 6 (nth (direction offset) 1)) 
            (/ bs 15) 
            0 
            300 false)

      (.arc (+ (- (+ left bs) 6) (nth (direction offset) 0)) 
            (+ top 6 (nth (direction offset) 1)) 
            (/ bs 15) 
            0 
            300 false)

      (.closePath)
      (.fill)))
  state)

(declare set-eaten set-next-level move-ghosts reset-ghost ghost-random-move update-ghosts check-collided)

(defn draw-ghosts [{ghosts :ghosts :as state}] 
  (letfn [(dg [g] (draw-ghost g state))]
    (dorun 
      (map dg ghosts))
    state))

(defn draw-board [state]
  (-> state draw-map draw-footer draw-pills draw-dialog))

(defn move [state]
  (-> state move-pacman move-ghosts)
  )

(defn set-attrs [state]
  (-> state check-collided set-eaten set-next-level))


(defn draw-agents [state]
  (-> state redraw-block draw-pacman draw-ghosts)
  )

(defn main-draw [state]
  (if (= :playing (:phase state))
    (-> state draw-board move set-attrs draw-agents)
    (draw-board state)))

;; =============================================================================
;; Event Handlers

(def keydown-state (atom nil))

(defn handle-keydown [e]
    (reset! keydown-state (.-keyCode e))
    (.preventDefault e)
    (.stopPropagation e))

;; =============================================================================
;; Update Functions & Helpers

(defn start-level [state]
  (-> state
    (assoc :timer-start (- (:tick state) 1))
    (assoc :phase :countdown)))

(defn start-new-game [state]
  (-> state
    (assoc :level 1)
    (start-level)))

(defn resume-game [state]
  (-> state 
    (assoc :dialog nil)
    (assoc :phase :playing)))

(defn pause-game [state]
  (-> state
    (assoc :dialog "Paused")
    (assoc :phase :pause)))

(defn toggle-pause [state]
  (if (= (:phase state) :pause)
    (resume-game state)
    (pause-game state)))

(defn keydown [state]
  (let [kc @keydown-state]
    (reset! keydown-state nil)
    (condp = kc
      (:N const/KEYS) (start-new-game state)
      (:S const/KEYS) (.setItem (.-localStorage (dom/getWindow)) "sound-disabled" false)
      (:P const/KEYS) (toggle-pause state)
      (if (and kc (not= (:phase state) :pause))
        (do 
          (assoc-in state [:user :due] (get controls kc)))
        state))))

(defn lose-life [state] state)

(declare is-vulnerable? is-dangerous? is-hidden? point-to-coord)


(defn collided? [upos gpos]
  (= (point-to-coord upos) (point-to-coord gpos)))

(defn check-collided [{user :user ghosts :ghosts :as state}]
  (let [upos (:position user)
         eat-pacman? #(and (collided? upos (:position %1)) (not (:eatable %1))) 
         eaten-by-pacman? #(and (collided? upos (:position %1)) (:eatable %1)) 
         set-ghost-eaten (fn [g] 
                           (if (eaten-by-pacman? g) 
                             (do 
                               {:eaten true}) 
                             {}))
         pacman-eaten? (some #{true} (map eat-pacman? ghosts))]
    (if pacman-eaten?
      (assoc state :phase :dying)
      (update-ghosts state set-ghost-eaten))))

(defn is-on-same-plane? [dir]
  (or (and (or (= dir :left) (= dir :right)))
      (and (or (= dir :up)   (= dir :down)))))

(defn on-whole-square? [n]
  (= (mod n 10) 0))

(defn on-grid-square? [{x :x y :y }]
  (and (on-whole-square? y) (on-whole-square? x)))

(defn point-val-to-coord [n] 
  (Math/round (/ n 10)))

(defn point-to-coord [{x :x y :y }]
  {:x (point-val-to-coord x) :y (point-val-to-coord y)})

(defn next-coord [{x :x y :y} dir]
  (case dir
    :left { :x (- x 1) :y y }
    :right { :x (+ x 1) :y y }
    :up { :x x :y (- y 1) }
    :down { :x x :y (+ y 1) }
    {:x x :y y})) 

(defn next-square [n dir]
  (let [rem (mod n 10)]
    (cond
      (= rem 0) n
      (or (= dir :right) (= dir :down)) (+ n (- 10 rem))
      :else (- n rem))))

(defn next-pos [{x :x y :y} dir]
  {:y (point-val-to-coord (next-square y dir))
   :x (point-val-to-coord (next-square x dir))})

;; Also used for moving Pac-Man. Helps determine if he is facing a wall.
;; Could be doing too many things at once...
(defn direction-allowable? [map dir pos]
  (and (or (is-on-same-plane? dir) 
           (on-grid-square? pos)) 
    (is-floor-space? map (next-coord (point-to-coord pos) dir))))


(defn board-pos [map {y :y x :x}]
  (get (get (:board map) y) x))

(defn within-bounds? [map {y :y x :x}]
  (and (>= y 0) (< y (:height map)) 
       (>= x 0) (< x (:width map))))

(defn is-wall-space? [map coord]
  (and (within-bounds? map coord) 
       (= const/WALL (board-pos map coord))))

(defn is-floor-space? [map coord]
  (if (within-bounds? map coord)
    (let [piece (board-pos map coord)]
      (or (= piece const/EMPTY)
          (= piece const/BISCUIT)
          (= piece const/PILL)))))

(defn draw-wall [map]
  (set! (. ctx -strokeStyle) "#0000FF")
  (set! (. ctx -lineWidth) 5)
  (set! (. ctx -lineCap) "round")
  (letfn [(*block-size [n] (* n (:block-size map)))]
    (doseq [line const/WALLS]
      (.beginPath ctx)
      (doseq [point line]
        (cond (:move point) (let [[a b] (:move point)]
                              (.moveTo ctx (*block-size a) (*block-size b)))
          (:line point) (let [[a b] (:line point)] 
                          (.lineTo ctx (*block-size a) (*block-size b)))
          (:curve point) (let [[a b c d] (:curve point)] 
                           (.quadraticCurveTo ctx 
                             (*block-size a)
                             (*block-size b)
                             (*block-size c)
                             (*block-size d)))))       
      (.stroke ctx))))

(defn draw-pills [{map :map :as state}]
  (let [height     (:height map)
        width      (:width map)
        block-size (:block-size map)]
    (doseq [i (range height)]
      (doseq [j (range width)]
        (if (= (board-pos map {:y i :x j}) const/PILL)
          (do
            (.beginPath ctx)
            (set! (. ctx -fillStyle) "#000")
            (.fillRect ctx (* j block-size) 
                           (* i block-size)
                           block-size 
                           block-size)
            (set! (. ctx -fillStyle) "#FFF")
            (.arc ctx (+ (* j block-size) (/ block-size 2))
                      (+ (* i block-size) (/ block-size 2))
                     (Math/abs (- 5 (/ (:pill-size map) 3)))
                      0
                      (* (.-PI js/Math) 2)
                      false)
            (.fill ctx)
            (.closePath ctx)))))
    state))

(defn draw-biscuit [{y :y x :x} layout {map :map :as state}]
  (let [block-size (:block-size map)]
    (set! (. ctx -fillStyle) "#000")
    (.fillRect ctx (* x block-size) (* y block-size) block-size block-size)
    (if (= layout const/BISCUIT)
      (do
        (set! (. ctx -fillStyle) "#FFF")
        (.fillRect ctx (+ (* x block-size) (/ block-size 2.5)) 
          (+ (* y block-size) (/ block-size 2.5))
          (/ block-size 6)
          (/ block-size 6))))
    state))

(defn draw-block [{map :map :as state} coord block-size] 
  (let [layout (board-pos map coord)]
    (if-not (= layout const/PILL) 
      (do   
        (.beginPath ctx)
        (if (or (= layout const/EMPTY)
                (= layout const/BLOCK)
                (= layout const/BISCUIT)) 
          (draw-biscuit coord layout state))
        (.closePath ctx)))
    state))

(defn block 
  "Turns x and y coordinates into tile value in the game board."
  [map coord]
  (board-pos map coord))

(defn add-score [{user :user map :map :as state} points]
  (let [score (:score user)
        new-score (assoc-in state [:user :score] (+ points score))]
    (if (and (>= score 10000) (< (- score points) 10000))
      (update-in new-score [:user :lives] inc)
      new-score)))

;; ============================================================================================
;; Move Pac-Man

(defn get-new-coord [dir {x :x y :y} speed]
  (case dir
    :left  {:x (- x speed) :y y}
    :right {:x (+ x speed) :y y}
    :up    {:x x :y (- y speed)}
    :down  {:x x :y (+ y speed)}
    {:x x :y y}))

;; ============================================================================================
;; Ghost pathfinding

(defn get-neighbors [map {x :x y :y}]
  (letfn [(legal? [coord] (within-bounds? map coord))
           (l? [coord] true)]
  (filter legal? [{:x (+ 1 x) :y y} {:x (- x 1) :y y} {:x x :y (+ 1 y)} {:x x :y (- y 1)}])))

(defn get-accessible-neighbors [map coord]
  (letfn [(path? [c] (some #(= (board-pos map c) %) [const/BISCUIT const/PILL const/EMPTY]))]
    (filter path? (get-neighbors map coord))))

(defn amatrix [map]
  (let [h (:height map)
        w (:width map)
        pairs (mapcat (fn [x] (map #(list % x) (range w))) (range h))
        coords (map (fn [p] {:x (first p) :y (second p)}) pairs)
        neighbors (map #(get-accessible-neighbors map %) coords)] 
    (zipmap coords neighbors) ;; This doesn't work right currently. 
 ))

(defn get-new-pos [dir {x :x y :y :as pos} speed]
  (cond 
    (and (= y 100) (>= x 176) (= dir :right)) {:y 100 :x -10}
    (and (= y 100) (<= x 4) (= dir :left))  {:y 100 :x 190}
    :else (get-new-coord dir pos speed)))

(defn get-new-direction [map due dir pos]
  (cond 
    (and dir (is-wall-space? map 
             (next-coord (point-to-coord pos) due)) 
             (not (is-wall-space? map (next-coord (point-to-coord pos) dir)))) dir
    (direction-allowable? map due pos) due))

(defn board-empty? [board]
  (nil? (some #{const/BISCUIT const/PILL} (flatten board))))

;; reset pacman to orig pos code for reseting ghosts
(defn set-next-level [{level :level map :map :as state}]
  (if (board-empty? (:board map))
    (-> state
      (update-in [:level] inc)
      (assoc-in [:map :board] const/game-map)
      (assoc-in [:user :position] {:x 90 :y 120})
      (update-ghosts reset-ghost)
      (assoc :phase :countdown))
    state))

(defn set-block [{x :x y :y} map type] 
  (assoc-in map [y x] type))

(declare make-ghost-eatable)
(defn set-eaten-helper [state coord board points]
      (-> state 
        (assoc-in [:map :board] (set-block coord board const/EMPTY))
        (update-in [:user :eaten] (fnil inc 0)) 
        (add-score points)))

(defn set-eaten [{user :user map :map :as state}]
  (let [{pos :position dir :direction} user
        {board :board} map
        coord (point-to-coord pos)
        block-pos (block map coord)]
    (condp = block-pos
      const/BISCUIT (set-eaten-helper state coord board 10) 
      const/PILL (-> state 
                   (set-eaten-helper coord board 50) 
                   (update-ghosts make-ghost-eatable)) 
      state)))

(defn nearest-10 [n]
  (* 10 (Math/round (/ n 10))))

(defn normalize-position [dir {x :x y :y}]
  (case dir 
    :left {:x x :y (nearest-10 y)}    
    :right {:x x :y (nearest-10 y)}    
    :up {:x (nearest-10 x) :y y}
    :down {:x (nearest-10 x) :y y}
    {:x x :y y}))

;; (defn old-refresh-user-data [{user :user map :map :as state}] 
;;   (let [{dir :direction pos :position 
;;          due :due speed :speed} user
;;          ndir (get-new-direction map due dir pos)
;;          npos (get-new-pos dir (normalize-position dir pos) speed)]    
;;     (if (= (:phase state) :playing)
;;       { :position  npos
;;         :old-pos   pos
;;         :direction ndir}
;;       {})))

(defn refresh-data [entity map phase dir-func]
  (let [{dir :direction pos :position 
         due :due speed :speed } entity
         ndir (dir-func map due dir pos)
         npos (get-new-pos dir (normalize-position dir pos) speed)]
    (if (= phase :playing)
      { :position  npos
        :old-pos   pos
        :direction ndir}
      {})))

(defn refresh-user-data [{map :map user :user phase :phase}]
  (refresh-data user map phase get-new-direction))

(declare get-random-direction)

(defn refresh-ghost-data [ghost {map :map phase :phase}]
  (refresh-data ghost map phase get-random-direction))

(defn move-pacman [{user :user :as state} ]
  (update-in state [:user]
    (fn [user]
      (merge user (refresh-user-data state)))))

(defn move-ghosts [{ghosts :ghosts :as state}]
  (letfn [(gd [g] (merge g (refresh-ghost-data g state)))]
    (update-in state [:ghosts] (fn [ghosts] (map gd ghosts)))))

(defn update-ghosts [state merge-fn]
  (update-in state [:ghosts]
    (fn [ghosts]
      ;(map #(merge % (merge-fn ghosts)) (:ghosts state))
      (map #(merge % (merge-fn %)) ghosts))))

;; ============================================================================================
;; Ghosts

;; =========
;; Rules

(defn is-vulnerable? [ghost]
  (nil? (:eatable ghost)))

(defn is-hidden? [ghost]
  (and (nil? (:eatable ghost)) (:eaten ghost)))

(defn is-dangerous? [ghost]
  (nil? (:eaten ghost)))

;; =========
;; Movement

(defn ghost-add-bounded 
  "Collision detection."
  [point speed]
  (let [rem (mod point 10)
        result (+ rem speed)]
    (cond (and (not= rem 0 )(> result 10)) (+ point (- 10 rem))
          (and (> rem 0) (< result 0)) (- point rem))
    :else (+ point speed)))

(defn ghost-get-new-coord [ghost dir {x :x y :y :as pos}]
  (let [speed (if (is-vulnerable? ghost) 
                (if (is-hidden? ghost) 4 2))
        x-speed (or 
                  (and (= dir :left) (- speed))
                  (and (= dir :right) speed) 
                  0)
         y-speed (or 
                  (and (= dir :down) speed)
                  (and (= dir :up) (- speed)) 
                  0)]
    {:x (ghost-add-bounded x x-speed) 
     :y (ghost-add-bounded y y-speed)}))

;; Most certainly broken.
(defn get-random-direction []
  (rand-nth [:up :down :left :right]))

(defn opposite-direction [ghost]
  (condp = (:direction ghost)
    :left :right
    :right :left
    :up :down
    :up))

(defn seconds-ago [tick]
  (/ (- get-tick tick) const/FPS))

(defn ghost-eatable-color [ghost]
  (if (> (seconds-ago (:eatable ghost)) 5)
    (if (> (mod get-tick 20) 10) "#FFFFFF" "#0000BB")
    "#0000BB"))

(defn get-color [ghost]
  (cond 
    (:eatable ghost) (ghost-eatable-color ghost)
    (:eaten ghost) "#222"
    :else (:color ghost)))

;; BAD.
(defn pane [pos dir]
  (cond 
   (and (= (:y pos) 100) (>= (:x pos) 190) (= dir (:right const/game-const))) {:y 100 :x -10}
   (and (= (:y pos) 100) (<= (:x pos) -10) (= dir (:left const/game-const))) {:y 100 :x 190}))
 
;; =====================================================
;; Ghost states

(defn reset-ghost [ghosts] 
  {:eaten nil, 
   :eatable nil, 
   :position {:x 90, :y 80}
   :direction (get-random-direction)})

(defn ghost-random-move [{dir :direction pos :position speed :speed}]
  {:direction (get-random-direction) 
   :position  (get-new-pos dir (normalize-position dir pos) speed)})

(defn make-ghost-eatable [ghost]
  {
   :eatable true
   :color "#cccccc"
})

;; =======================================================
;; Game Phases

(defn game-over [state]
  (-> state 
    (assoc :dialog "Press N to start a new game")
    (assoc :phase :waiting)
    (assoc-in [:user :lives] 3)
    (assoc-in [:user :score] 0)
    (assoc-in [:map :board] const/game-map)))

(defn start-game [state]
  (-> state
    (assoc :state-changed false)))

(defn game-playing [state]
  (-> state
    (assoc :phase :playing)))

(def ticks-remaining (atom 0))

(defn pacman-dying [state]
    (-> state
      (assoc-in [:user :position] {:x 90 :y 120})
      (update-in [:user :lives] dec) ;;doesn't work.
      (update-ghosts reset-ghost)
      (assoc :phase :playing)))

(defn advanced-dying [state]
  (if (zero? @ticks-remaining)
    (if (= (:dying-state state) 0)
      (do
        (reset! ticks-remaining 0)
        (-> state 
          (assoc :phase :playing)
          (assoc :dying-state 3)))
      (do 
        (reset! ticks-remaining const/FPS)))
    (do
      (-> state 
        (draw-dead @ticks-remaining)
        (update-in [:dying-state] dec))
      (swap! ticks-remaining dec)
      state)))

(defn game-countdown [state]
  (if (zero? @ticks-remaining)
    (if (= (:countdown state) 1)
      (do
        (reset! ticks-remaining 0)
        (-> state game-playing (assoc :dialog nil) (assoc :countdown 4)))
      (do 
        (reset! ticks-remaining const/FPS)
        (-> state 
          (update-in [:countdown] dec) 
          (assoc :dialog (str "Starting in: " (dec (:countdown state)))))))
    (do
      (swap! ticks-remaining dec)
      state)))
 
(defn eaten-pill [state]
  (-> state
    (assoc :timer-start (:tick state))))

;; =========================================================
;; Main Game Loop

(defn driver  [state]
  (let [phase (:phase state)
        state (-> (if (= phase :pause) 
                    state
                    (update-in state [:tick] (fnil inc 0)))
                (keydown))]
    (main-draw
      (cond 
        (= phase :playing) state;(game-playing state)
        (and (= phase :waiting) (:state-changed state)) (start-game state) 
        (and (= phase :eaten-pause) 
          (> (- (:tick state) (:timer-start state)) (* const/FPS 2))) (game-playing state)
        (and (= phase :dying) (= 1 (:lives (:user state)))) (game-over state) 
        (= phase :dying) (pacman-dying state)
        (= phase :countdown) (game-countdown state)
        (= phase :next-level) (start-game state) 
        :else state))))

(defn make-state []
  (-> game-state
    (assoc :dialog "Press N to start a new game")
    (assoc-in [:user :position] {:x 90 :y 120})
    (update-ghosts reset-ghost)
    (assoc-in [:map :width] 19)
    (assoc-in [:map :height] 22)
    (assoc-in [:map :block-size] 18)))

(defn loaded []
  (let [init-state (make-state)
        interval   (/ 1000 const/FPS)]
    (.addEventListener js/document "keydown" handle-keydown true)
    (.setTimeout js/window
      (fn game-loop [s]
        (let [state (or s init-state)
              new-state (driver state)]
          (.setTimeout js/window
            #(game-loop new-state)
            interval)))
      interval)))

(defn init [wrapper root]
  (let [block-size (/ (.-offsetWidth wrapper) 19)]
    (.setAttribute canvas "width" (str (* block-size 19) "px"))
    (.setAttribute canvas "height" (str (+ (* block-size 22) 30) "px"))
    (.appendChild wrapper canvas)
    ;; a bit trickier maybe handle as special? - David
    ;;(dialog {} "Loading...")
    (loaded)))

;; Init!
(def elem (helper/get-element-by-id "pacman"))
(.setTimeout js/window (fn [x] (init elem "./")) 0)
