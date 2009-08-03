(ns clj-jogl
  (:import [java.awt Frame]
           [java.awt.event WindowAdapter MouseListener
                           MouseMotionListener MouseWheelListener]
           [com.sun.opengl.util FPSAnimator]
           [javax.media.opengl GLCanvas GLEventListener GL]
           [javax.media.opengl.glu GLU]
           [java.io File FileWriter]))

(defn get-window-adapter [frame animator]
  (proxy [WindowAdapter] []
    (windowClosing [e] (do
                         (.stop animator)
                         (.setVisible frame false)))))

(.delete (File. "C:\\log.log"))
(defn log [& strings]
  (doto (FileWriter. "C:\\log.log" true)
    (.write (apply str strings))
    (.write "\r\n")
    (.close))
  nil)

(defn log-matrix [m]
  (log (nth m  0) " " (nth m  1) " " (nth m  2) " " (nth m  3))
  (log (nth m  4) " " (nth m  5) " " (nth m  6) " " (nth m  7))
  (log (nth m  8) " " (nth m  9) " " (nth m 10) " " (nth m 11))
  (log (nth m 12) " " (nth m 13) " " (nth m 14) " " (nth m 15)))

(def g-foo (ref {:x 0 :y 0}))

(defn handle-mouse-pressed [event]
  (let [e (:data event)
        #^GLU glu (GLU.)
        #^GL  gl (GLU/getCurrentGL)
        viewport (make-array Integer/TYPE 4)
        modelview (make-array Double/TYPE 16)
        projection (make-array Double/TYPE 16)
        world-coords-near (make-array Double/TYPE 4)
        world-coords-far  (make-array Double/TYPE 4)
        x (double (.getX e))
        y (double (.getY e))
        z (double (- (:z @g-camera)))]
    (doto gl
      (.glMatrixMode GL/GL_MODELVIEW)
      (.glGetIntegerv GL/GL_VIEWPORT          viewport   0)
      (.glGetDoublev  GL/GL_MODELVIEW_MATRIX  modelview  0)
      (.glGetDoublev  GL/GL_PROJECTION_MATRIX projection 0))
    (log "x: " x)
    (log "y: " (- (nth viewport 3) y))
    (log "z: " z)
    (log "modelview:")
    (log-matrix modelview)
    (log "projection:")
    (log-matrix projection)
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
    (log "near: ")
    (log (nth world-coords-near 0) " "
         (nth world-coords-near 1) " "
         (nth world-coords-near 2) " "
         (nth world-coords-near 3) " ")
    (log "far: ")
    (log (nth world-coords-far 0) " "
         (nth world-coords-far 1) " "
         (nth world-coords-far 2) " "
         (nth world-coords-far 3) " ")
    (dosync (alter g-foo (fn [_] {:x (* (+ (nth world-coords-near 0)
                                           (nth world-coords-far 0))
                                        (/ z 10000.0))
                                  :y (* (+ (nth world-coords-near 1)
                                           (nth world-coords-far 1))
                                        (/ z 10000.0))})))))

(def g-events (ref []))

(defn fire-event [event]
  (dosync (alter g-events conj event)))

(defn process-event [event]
;  (log event)
  (cond (= "mousePressed" (:type event)) (handle-mouse-pressed event)
        :else (log "Unhandled event: " event)))

(defn process-events []
  (doseq [event @g-events]
    (process-event event))
  (dosync (alter g-events (fn [_] []))))

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
      (.glFrustum (- 0 a) a -1 1 1 10000)
      (.glMatrixMode GL/GL_MODELVIEW)
      (.glLoadIdentity))))

(defn get-time [& _] (/ (System/nanoTime) 1000000))
(def t (ref (get-time)))
(def x (ref 1))
(def g-camera (ref {:x 0 :y 0 :z -10}))

(defn gl-display [canvas]
  (process-events)
;  (log "gl-display" canvas)
  (let [#^GL gl (.getGL canvas)
        dt (- (get-time) @t)]
;    (log (double dt))
    (dosync (alter t get-time))
    (dosync (alter x + (/ dt 50.0)))
;    (when (> @x 1) (dosync (alter x (fn [_] -1))))
    (doto gl
      (.glClearColor 0 0 0 0)
      (.glClear (bit-or GL/GL_COLOR_BUFFER_BIT GL/GL_DEPTH_BUFFER_BIT))
      (.glMatrixMode GL/GL_MODELVIEW)
      (.glLoadIdentity)
      (.glPushMatrix)
      (.glTranslatef (:x @g-camera)
                     (:y @g-camera)
                     (:z @g-camera))
      (.glPushMatrix)
      (.glTranslatef (:x @g-foo) (:y @g-foo) 0)
      (.glBegin GL/GL_POLYGON)
      (.glColor3f   1  0 0)
      (.glVertex3f -5 -5 0)
      (.glColor3f   0  1 0)
      (.glVertex3f  5 -5 0)
      (.glColor3f   1  1 1)
      (.glVertex3f  5  5 0)
      (.glColor3f   0  0 1)
      (.glVertex3f -5  5 0)
      (.glEnd)
      (.glPopMatrix)
      (.glPopMatrix))))

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
  (def mx (.getX e))
  (def my (.getY e))
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
  (let [dx (/ (- (.getX e) mx) 15) 
        dy (/ (- my (.getY e)) 15)
        cx (:x @g-camera)
        cy (:y @g-camera)]
;    (dosync (alter g-camera assoc :x (+ cx dx)
;                                  :y (+ cy dy))))
  (def mx (.getX e))
  (def my (.getY e))))

(defn mouse-moved [e]
;  (log "mouseMoved:" e)
  )

(defn mouse-wheel-moved [e]
;  (log "mouseWheelMoved:" e)
  (let [dz (/ (.getWheelRotation e) 2)
        cz (:z @g-camera)]
    (dosync (alter g-camera assoc :z (+ cz dz)))))


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
        animator (FPSAnimator. canvas 60)
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