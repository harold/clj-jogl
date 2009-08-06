(ns clj-jogl
  (:import [java.awt Frame]
           (java.awt.event WindowAdapter MouseListener 
			   MouseMotionListener MouseWheelListener
			   KeyAdapter)
           [com.sun.opengl.util FPSAnimator]
           [javax.media.opengl GLCanvas GLEventListener GL]
           [javax.media.opengl.glu GLU]
           [java.io File FileWriter])
  (:require [clj-jogl-gl :as jgl]
	    [clj-jogl-commands :as jcmd]))

(load-file "log.clj")
(load-file "srg_object.clj")

(def g-object (srg-object/from-xml "island.xml"))
(def g-camera (ref {:x  0 :y  0 :z  -10
                    :tx 0 :ty 0 :tz -10}))
(def g-cmd-system (jcmd/create-command-system g-camera))
(def g-events (ref []))

(defn get-window-adapter [frame animator]
  (proxy [WindowAdapter] []
    (windowClosing [e] (do
                         (.stop animator)
                         (.setVisible frame false)))))

(defn handle-mouse-pressed [event]
  (let [e (:data event)
	x (.getX e)
	y (.getY e)]
    (jcmd/fire-command g-cmd-system {:type :relative-drag-start 
				     :drag-start [x y] })))

(defn handle-mouse-dragged [event]
  (let [e (:data event)
	x (.getX e)
	y (.getY e)]
    (jcmd/fire-command g-cmd-system {:type :relative-drag
				     :data [x y] })))

(defn handle-key-typed [event]
  (let [e (:data event)
	ch (.getKeyChar e)]
    (cond (= ch \q) (jcmd/spew-commands g-cmd-system)
	  (= ch \w) (jcmd/replay-commands g-cmd-system))))

(defn fire-event [event]
  (dosync (alter g-events conj event)))

(defn process-event [event]
;  (log event)
  (cond (= "mousePressed" (:type event)) (handle-mouse-pressed event)
        (= "mouseDragged" (:type event)) (handle-mouse-dragged event)
	(= "keyTyped" (:type event)) (handle-key-typed event)
        :else (log "Unhandled event: " event)))

(defn process-events []
  (doseq [event @g-events]
    (process-event event))
  (dosync (alter g-events (fn [_] []))))

(defn tick [dt]
;  (log (double dt))
  (let [z (:z @g-camera)
        tz (:tz @g-camera)
        dz (Math/abs (- z tz))]
    (if (< 0.001 dz)
          (dosync (alter g-camera assoc :z (+ z (/ (- tz z) 10.0)))))))

(defn gl-init [canvas]
;  (log "gl-init:" canvas)
  (let [gl (.getGL canvas)]
    (doto gl
      (.setSwapInterval 1)
      (.glEnable GL/GL_CULL_FACE)
      (.glDisable GL/GL_DEPTH_TEST))))

(defn gl-reshape [canvas x y w h]
;  (log "gl-reshape:" canvas x y w h)
  (let [gl (.getGL canvas)
        a (double (/ w h))]
    (doto gl
      (.glMatrixMode GL/GL_PROJECTION)
      (.glLoadIdentity)
      (.glFrustum (- 0 a) a -1 1 1 1000)
      (.glMatrixMode GL/GL_MODELVIEW)
      (.glLoadIdentity))))

(defn get-time [& _] (/ (System/nanoTime) 1000000))
(def t (ref (get-time)))

(defn draw-object [#^GL gl object]
  (doseq [polygon object]
      (.glBegin gl GL/GL_POLYGON)
      (doseq [vertex polygon]
        (let [color (:color vertex)
              position (:position vertex)
              r (:r color)
              g (:g color)
              b (:b color)
              x (:x position)
              y (:y position)]
          (.glColor3f  gl r g b)
          (.glVertex3f gl x y 0)))
      (.glEnd gl)))

(defn gl-display [canvas]
  (process-events)
;  (log "gl-display" canvas)
  (let [#^GL gl (.getGL canvas)
             dt (- (get-time) @t)]
    (tick dt)
    (doto gl
      (.glClearColor 0.1 0.1 0.1 0)
      (.glClear (bit-or GL/GL_COLOR_BUFFER_BIT GL/GL_DEPTH_BUFFER_BIT))
      (.glMatrixMode GL/GL_MODELVIEW)
      (.glLoadIdentity)
      (.glPushMatrix)
      (.glTranslatef (- (:x @g-camera))
                     (- (:y @g-camera))
                     (:z @g-camera))
      (draw-object g-object)
      (.glPopMatrix))
    (dosync (alter t get-time))))

(defn get-gl-app []
  (proxy [GLEventListener] []
    (init [canvas] (gl-init canvas))
    (reshape [canvas x y w h] (gl-reshape canvas x y w h))
    (display [canvas] (gl-display canvas))
    (displayChanged [canvas mode-changed device-changed]
      (log "DisplayChanged:" canvas mode-changed device-changed))))

(defn mouse-entered [e]
;  (log "mouseEntered:" e)
  )

(defn mouse-exited [e]
;  (log "mouseExited:" e)
  )

(defn mouse-pressed [e]
;  (log "mousePressed:" e)
  (fire-event {:type "mousePressed" :data e}))

(defn mouse-released [e]
;  (log "mouseReleased:" e)
  )

(defn mouse-clicked [e]
;  (log "mouseClicked:" e)
  )

(defn mouse-dragged [e]
;  (log "mouseDragged:" e)
  (fire-event {:type "mouseDragged" :data e}))

(defn mouse-moved [e]
;  (log "mouseMoved:" e)
  )

(defn mouse-wheel-moved [e]
;  (log "mouseWheelMoved:" e)
  (let [dz (* -3 (.getWheelRotation e))
        cz (:z @g-camera)]
    (dosync (alter g-camera assoc :tz (+ cz dz)))))

(defn get-mouse-handler []
  (proxy [MouseListener MouseMotionListener MouseWheelListener] []
    (mouseEntered    [e] (mouse-entered e))
    (mouseExited     [e] (mouse-exited e))
    (mousePressed    [e] (mouse-pressed e))
    (mouseReleased   [e] (mouse-released e))
    (mouseClicked    [e] (mouse-clicked e))
    (mouseDragged    [e] (mouse-dragged e))
    (mouseMoved      [e] (mouse-moved e))
    (mouseWheelMoved [e] (mouse-wheel-moved e))))

(defn key-typed [e]
  (fire-event {:type "keyTyped" :data e}))

(defn get-key-handler []
  (proxy [KeyAdapter] []
    (keyTyped [e] (key-typed e))))

(defn go []
  (let [frame (Frame. "clj-jogl")
        canvas (GLCanvas.)
        animator (FPSAnimator. canvas 60 true)
        mouse-handler (get-mouse-handler)]
    (.addGLEventListener canvas (get-gl-app))
    (.start animator)
    (doto canvas
      (.addMouseListener mouse-handler)
      (.addMouseMotionListener mouse-handler)
      (.addMouseWheelListener mouse-handler)
      (.addKeyListener (get-key-handler)))
    (doto frame
      (.addWindowListener (get-window-adapter frame animator))
      (.add canvas)
      (.setSize (+ 800 16) (+ 450 36))
      (.setIgnoreRepaint true)
      (.show))))


(log "=== begin ===")
(go)
(log "=== end ===")