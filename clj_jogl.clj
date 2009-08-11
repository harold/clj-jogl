(ns clj-jogl
  (:import (java.awt Frame)
           (java.awt.event WindowAdapter MouseAdapter KeyAdapter)
           (com.sun.opengl.util FPSAnimator)
           (javax.media.opengl GLCanvas GLEventListener GL)
           (javax.media.opengl.glu GLU)
           (java.io File FileWriter))
  (:require [clj-jogl-app :as app]
            [clj-jogl-commands :as jcmd]
            [clj-jogl-tess :as jtess]
            [log :as log]))

(def g-app (app/create))
(app/load-doc g-app "island.xml")
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
    (jcmd/fire-command (app/get-cmd-system g-app) {:type :relative-drag-start
                                                   :drag-start [x y]})))

(defn handle-mouse-dragged [event]
  (let [e (:data event)
        x (.getX e)
        y (.getY e)]
    (when (= :pan (:mode @g-app))
      (jcmd/fire-command (app/get-cmd-system g-app) {:type :relative-drag
                                                     :data [x y]}))))

(defn handle-mouse-wheel-moved [event]
  (let [e (:data event)
        direction (.getWheelRotation e)]
    (jcmd/fire-command (app/get-cmd-system g-app) {:type :relative-zoom
                                                   :data direction})))

(defn handle-key-typed [event]
  (let [e (:data event)
        ch (.getKeyChar e)]
    (cond (= ch \q) (jcmd/spew-commands   (app/get-cmd-system g-app))
          (= ch \w) (jcmd/replay-commands (app/get-cmd-system g-app)))))

(defn handle-key-pressed [event]
  (let [e (:data event)
        ch (.getKeyChar e)]
    (cond (= ch \space) (app/set-mode g-app :pan))))

(defn handle-key-released [event]
  (let [e (:data event)
        ch (.getKeyChar e)]
    (cond (= ch \space) (app/set-mode g-app :normal))))

(defn fire-event [event]
  (dosync (alter g-events conj event)))

(defn process-event [event]
  (cond (= "mousePressed"    (:type event)) (handle-mouse-pressed event)
        (= "mouseDragged"    (:type event)) (handle-mouse-dragged event)
        (= "mouseWheelMoved" (:type event)) (handle-mouse-wheel-moved event)
        (= "keyTyped"        (:type event)) (handle-key-typed event)
        (= "keyPressed"      (:type event)) (handle-key-pressed event)
        (= "keyReleased"     (:type event)) (handle-key-released event)
        :else (log/log "Unhandled event: " event)))

(defn process-events []
  (doseq [event @g-events]
    (process-event event))
  (dosync (ref-set g-events [])))

(defn tick [dt]
;  (log/log (double dt))
  (let [z (:z @(app/get-camera g-app))
        tz (:tz @(app/get-camera g-app))
        dz (Math/abs (- z tz))]
    (if (< 0.001 dz)
      (app/set-camera-property g-app :z (+ z (/ (- tz z) 10.0))))))

(defn gl-init [canvas]
  (let [gl (.getGL canvas)]
    (doto gl
      (.setSwapInterval 1)
      (.glEnable GL/GL_CULL_FACE)
      (.glDisable GL/GL_DEPTH_TEST))))

(defn gl-reshape [canvas x y w h]
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

(defn render-doc [#^GL gl app-ref]
  (let [doc (app/get-doc app-ref)]
    (doseq [poly-ref doc]
      (let [poly @poly-ref]
        (if (= nil (:tess poly))
          (dosync (alter poly-ref assoc :tess (jtess/tess (:verts poly))))))
      (let [chunk-list-ref (:tess @poly-ref)]
        (doseq [chunk-ref @chunk-list-ref]
          (let [chunk @chunk-ref]
            (.glBegin gl (:type chunk))
            (doseq [vertex (:verts chunk)]
              (let [color (:color vertex)
                    position (:position vertex)
                    r (:r color)
                    g (:g color)
                    b (:b color)
                    x (:x position)
                    y (:y position)]
                (.glColor3f  gl r g b)
                (.glVertex3f gl x y 0)))
            (.glEnd gl)))))))

(defn gl-display [canvas]
  (process-events)
  (let [#^GL gl (.getGL canvas)
        dt (- (get-time) @t)]
    (tick dt)
    (doto gl
      (.glClearColor 0.1 0.1 0.1 0)
      (.glClear (bit-or GL/GL_COLOR_BUFFER_BIT GL/GL_DEPTH_BUFFER_BIT))
      (.glMatrixMode GL/GL_MODELVIEW)
      (.glLoadIdentity)
      (.glPushMatrix)
      (.glTranslatef (- (:x @(app/get-camera g-app)))
                     (- (:y @(app/get-camera g-app)))
                        (:z @(app/get-camera g-app)))
      (render-doc g-app)
      (.glPopMatrix))
    (dosync (alter t get-time))))

(defn get-gl-app []
  (proxy [GLEventListener] []
    (init [canvas] (gl-init canvas))
    (reshape [canvas x y w h] (gl-reshape canvas x y w h))
    (display [canvas] (gl-display canvas))
    (displayChanged [canvas mode-changed device-changed]
      (log/log "DisplayChanged:" canvas mode-changed device-changed))))

(defn get-mouse-handler []
  (proxy [MouseAdapter] []
    (mousePressed    [e] (fire-event {:type "mousePressed"    :data e}))
    (mouseDragged    [e] (fire-event {:type "mouseDragged"    :data e}))
    (mouseWheelMoved [e] (fire-event {:type "mouseWheelMoved" :data e}))))

(defn get-key-handler []
  (proxy [KeyAdapter] []
    (keyTyped    [e] (fire-event {:type "keyTyped"    :data e}))
    (keyPressed  [e] (fire-event {:type "keyPressed"  :data e}))
    (keyReleased [e] (fire-event {:type "keyReleased" :data e}))))

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

(go)
