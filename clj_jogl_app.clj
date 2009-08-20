(ns clj-jogl-app
  (:require [clj-jogl-gl :as jgl]
            [clj-jogl-tess :as jtess]
            [srg-object :as srg-object])
  (:import (java.io File FileWriter FileReader PushbackReader)))

(defn create []
  (ref {:mode             :normal
        :tool             :select
        :doc              nil
        :camera           {:x  0 :y  0 :z  -10
                           :tx 0 :ty 0 :tz -10}
        :vertex-selection {}
        :commands         []}))

; Getters/Setters
(defn get-mode             [app] (:mode   @app))
(defn get-tool             [app] (:tool   @app))
(defn get-doc              [app] (:doc    @app))
(defn get-camera           [app] (:camera @app))
(defn get-vertex-selection [app] (:vertex-selection @app))
(defn set-mode [app mode] (dosync (alter app assoc :mode mode)))
(defn set-tool [app tool] (dosync (alter app assoc :tool tool)))

(defn set-camera-property [app property value]
  (dosync (alter app assoc :camera (assoc (get-camera app) property value))))

; srg loading
(defn get-polygon [vert-list]
  {:tess nil
   :vertices vert-list})

(defn get-polygons [poly-list]
  (map get-polygon poly-list))

(defn new-doc [object]
  {:polygons (get-polygons object)})

(defn load-doc [app path]
  (let [object (srg-object/from-xml path)]
    (dosync (alter app assoc :doc (new-doc object)))))

; tess
(defn poly-tess [poly]
  (if (= nil (:tess poly))
    (do
      (assoc poly :tess (jtess/tess (:vertices poly))))
    poly))

(defn doc-tess [doc]
  {:polygons (doall (map poly-tess (:polygons doc)))})

(defn tess [app]
  (dosync (alter app assoc :doc (doc-tess (get-doc app)))))

; selection
(defn get-vertex-distance-pair [[x y] vertex]
  (let [vx (:x (:position vertex))
        vy (:y (:position vertex))
        dx (- x vx)
        dy (- y vy)
        d (+ (* dx dx) (* dy dy))]
    [d vertex]))

(defn select-nearest-vertex-helper [app x y]
  (let [vert-list (mapcat :vertices (:polygons (get-doc app)))
        pair-list (map get-vertex-distance-pair (repeat [x y]) vert-list)
        winner (first (sort-by first pair-list))]
    (dosync (alter app assoc :vertex-selection (second winner)))))

; commands
(defn relative-drag-start [app-ref [x y]]
  (let [camera (:camera @app-ref)
        world-point (jgl/screen-to-world x
                                         y
                                         (double (- (:z camera)))
                                         camera)]
    (dosync (alter app-ref assoc :drag-start [(:x world-point) (:y world-point)]))))

(defn relative-drag [app-ref cmd]
  (let [[cmd-x cmd-y] (:data cmd)
        app @app-ref
        camera (:camera app)
        [start-x start-y] (:drag-start app)
        world-point (jgl/screen-to-world cmd-x
                                         cmd-y
                                         (double (- (:z camera)))
                                         camera)]
    (let [dx (- start-x (:x world-point))
          dy (- (:y world-point) start-y)
          cx (:x camera)
          cy (:y camera)]
      (set-camera-property app-ref :x (+ cx dx))
      (set-camera-property app-ref :y (- cy dy)))
    (relative-drag-start app-ref [cmd-x cmd-y])))

(defn relative-zoom [app-ref cmd]
  (let [direction (:data cmd)
        camera (:camera @app-ref)
        dz (* -10 direction)
        cz (:z camera)]
    (set-camera-property app-ref :tz (+ cz dz))))

(defn select-nearest-vertex [app-ref [x y]]
  (let [app @app-ref
        camera (:camera app)
        world-point (jgl/screen-to-world x
                                         y
                                         (double (- (:z camera)))
                                         camera)]
    (select-nearest-vertex-helper app-ref (:x world-point) (:y world-point))))

(defn fire-command
  "Fire off a command. This will ready the command for execution"
  [app-ref cmd]
  (let [cmd-type (:type cmd)]
    (cond
      (= cmd-type :relative-drag-start)   (relative-drag-start app-ref (:data cmd))
      (= cmd-type :relative-drag)         (relative-drag app-ref cmd)
      (= cmd-type :relative-zoom)         (relative-zoom app-ref cmd)
      (= cmd-type :select-nearest-vertex) (select-nearest-vertex app-ref (:data cmd))))
  (dosync (alter app-ref assoc :commands (conj (:commands @app-ref) cmd))))

(defn spew-commands
  [app-ref]
  (with-open [fw (FileWriter. "c:\\commands.txt")]
    (binding [*out* fw]
      (prn (:commands @app-ref)))))

(defn replay-commands
  [app-ref]
  (try
   (with-open [fr (FileReader. "c:\\commands.txt")]
     (let [cmds (read (PushbackReader. fr))]
       (doseq [cmd cmds] (fire-command app-ref cmd))))
   (dosync (alter app-ref assoc :commands []))
   (catch Exception e (.printStackTrace e))))
