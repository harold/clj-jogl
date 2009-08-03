(ns clj-jogl
  (:import [java.awt Frame]
           [java.awt.event WindowAdapter MouseListener
                           MouseMotionListener MouseWheelListener]
           [com.sun.opengl.util FPSAnimator]
           [javax.media.opengl GLCanvas GLEventListener GL]
           [javax.media.opengl.glu GLU]
           [java.io File FileWriter]))

(load-file "log.clj")

(def g-camera (ref {:x  0 :y  0 :z  -10
                    :tx 0 :ty 0 :tz -10}))
(def g-events (ref []))

(defn get-window-adapter [frame animator]
  (proxy [WindowAdapter] []
    (windowClosing [e] (do
                         (.stop animator)
                         (.setVisible frame false)))))

(defn screen-to-world [x y z]
  (let [#^GLU glu (GLU.)
        #^GL  gl  (GLU/getCurrentGL)
        viewport   (make-array Integer/TYPE 4)
        modelview  (make-array Double/TYPE  16)
        projection (make-array Double/TYPE  16)
        world-coords-near (make-array Double/TYPE 4)
        world-coords-far  (make-array Double/TYPE 4)]
    (doto gl
      (.glMatrixMode GL/GL_MODELVIEW)
      (.glLoadIdentity)
      (.glPushMatrix)
      (.glTranslatef (- (:x @g-camera))
                     (- (:y @g-camera))
                     (:z @g-camera))
      (.glGetDoublev  GL/GL_MODELVIEW_MATRIX  modelview  0)
      (.glPopMatrix)
      (.glGetIntegerv GL/GL_VIEWPORT          viewport   0)
      (.glGetDoublev  GL/GL_PROJECTION_MATRIX projection 0))
    (.gluUnProject glu x (- (nth viewport 3) y) 0
                   modelview         (int 0)
                   projection        (int 0)
                   viewport          (int 0)
                   world-coords-near (int 0))
    (.gluUnProject glu x (- (nth viewport 3) y) 1.0
                   modelview         (int 0)
                   projection        (int 0)
                   viewport          (int 0)
                   world-coords-far  (int 0))
    {:x (+ (nth world-coords-near 0)
           (* (- (nth world-coords-far 0)
                 (nth world-coords-near 0))
              (/ z 999.0)))
     :y (+ (nth world-coords-near 1)
           (* (- (nth world-coords-far 1)
                 (nth world-coords-near 1))
              (/ z 999.0)))}))

(defn update-mouse-xy [event]
  (let [e (:data event)
        world-point (screen-to-world (.getX e)
                                     (.getY e)
                                     (double (- (:z @g-camera))))]
    (def mx (:x world-point))
    (def my (:y world-point))))

(defn handle-mouse-pressed [event]
  (update-mouse-xy event))

(defn handle-mouse-dragged [event]
  (let [e (:data event)
        world-point (screen-to-world (.getX e)
                                     (.getY e)
                                     (double (- (:z @g-camera))))]
    (let [dx (- mx (:x world-point))
          dy (- (:y world-point) my)
          cx (:x @g-camera)
          cy (:y @g-camera)]
      (dosync (alter g-camera assoc :x (+ cx dx)
                                    :y (- cy dy))))
    (update-mouse-xy event)))

(defn fire-event [event]
  (dosync (alter g-events conj event)))

(defn process-event [event]
;  (log event)
  (cond (= "mousePressed" (:type event)) (handle-mouse-pressed event)
        (= "mouseDragged" (:type event)) (handle-mouse-dragged event)
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
      (.glEnable GL/GL_DEPTH_TEST))))

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

(defn gl-display [canvas]
  (process-events)
;  (log "gl-display" canvas)
  (let [#^GL gl (.getGL canvas)
             dt (- (get-time) @t)]
    (tick dt)
    (doto gl
      (.glClearColor 0 0 0 0)
      (.glClear (bit-or GL/GL_COLOR_BUFFER_BIT GL/GL_DEPTH_BUFFER_BIT))
      (.glMatrixMode GL/GL_MODELVIEW)
      (.glLoadIdentity)
      (.glPushMatrix)
      (.glTranslatef (- (:x @g-camera))
                     (- (:y @g-camera))
                     (:z @g-camera))
      (.glPushMatrix)
      (.glBegin GL/GL_POLYGON)
      (.glColor3f   1  0 0)
      (.glVertex3f -5 -5 0)
      (.glColor3f   1  0.5 0)
      (.glVertex3f  5 -5 0)
      (.glColor3f   0.5 0.5 0.5)
      (.glVertex3f  5  5 0)
      (.glColor3f   0.2 0.2 0.4)
      (.glVertex3f -5  5 0)
      (.glEnd)
      (.glPopMatrix)
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
  (let [dz (* 2 (.getWheelRotation e))
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
      (.addMouseWheelListener mouse-handler))
    (doto frame
      (.addWindowListener (get-window-adapter frame animator))
      (.add canvas)
      (.setSize (+ 800 16) (+ 450 36))
      (.setIgnoreRepaint true)
      (.show))
    (loop []
      (when (.isVisible frame)
        (Thread/sleep 1000)
        (recur)))))

(log "=== begin ===")
(go)
(log "=== end ===")