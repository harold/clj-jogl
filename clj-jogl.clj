(ns clj-jogl
  (:import [java.awt Frame]
           [java.awt.event WindowAdapter MouseListener
                           MouseMotionListener MouseWheelListener]
           [com.sun.opengl.util FPSAnimator]
           [javax.media.opengl GLCanvas GLEventListener GL]
           [javax.media.opengl.glu GLU]))

(defn get-window-adapter [frame animator]
  (proxy [WindowAdapter] []
    (windowClosing [e] (do
                         (.stop animator)
                         (.setVisible frame false)))))

(defn gl-init [canvas]
;  (println "gl-init:" canvas)
  (let [gl (.getGL canvas)]
    (doto gl
      (.setSwapInterval 1)
      (.glEnable GL/GL_CULL_FACE)
      (.glEnable GL/GL_DEPTH_TEST))))

(defn gl-reshape [canvas x y w h]
;  (println "gl-reshape:" canvas x y w h)
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
;  (println "gl-display" canvas)
  (let [#^GL gl (.getGL canvas)
        dt (- (get-time) @t)]
;    (println (double dt))
    (dosync (alter t get-time))
    (dosync (alter x + (/ dt 50.0)))
;    (when (> @x 1) (dosync (alter x (fn [_] -1))))
    (doto gl
      (.glClearColor 0 0 0 0)
      (.glClear (bit-or GL/GL_COLOR_BUFFER_BIT GL/GL_DEPTH_BUFFER_BIT))
      (.glMatrixMode GL/GL_MODELVIEW)
      (.glLoadIdentity)
      (.glTranslatef (:x @g-camera)
                     (:y @g-camera)
                     (:z @g-camera))
      (.glPushMatrix)
      (.glTranslatef -10 0 0)
      (.glRotatef @x 0 0 1)
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
      (.glTranslatef 10 0 0)
      (.glRotatef @x 0 0 1)
      (.glBegin GL/GL_POLYGON)
      (.glColor3f   1  0 0)
      (.glVertex3f -5 -5 0)
      (.glColor3f   0  1 0)
      (.glVertex3f  5 -5 0)
      (.glColor3f   1  1 1)
      (.glVertex3f  5  5 0)
      (.glColor3f   0  0 1)
      (.glVertex3f -5  5 0)
      (.glEnd))))

(defn get-gl-app []
  (proxy [GLEventListener] []
    (init [canvas] (gl-init canvas))
    (reshape [canvas x y w h] (gl-reshape canvas x y w h))
    (display [canvas] (gl-display canvas))
    (displayChanged [canvas mode-changed device-changed]
      (println "DisplayChanged:" canvas mode-changed device-changed))))

(defn mouse-entered [e]
;  (println "mouseEntered:" e)
  )

(defn mouse-exited [e]
;  (println "mouseExited:" e)
  )

(defn mouse-pressed [e]
  (def mx (.getX e))
  (def my (.getY e))
;  (println "mousePressed:" e)
  )

(defn mouse-released [e]
;  (println "mouseReleased:" e)
  )

(defn mouse-clicked [e]
;  (println "mouseClicked:" e)
  )

(defn mouse-dragged [e]
;  (println "mouseDragged:" e)
  (let [dx (/ (- (.getX e) mx) 15) 
        dy (/ (- my (.getY e)) 15)
        cx (:x @g-camera)
        cy (:y @g-camera)]
    (dosync (alter g-camera assoc :x (+ cx dx)
                                  :y (+ cy dy))))
  (def mx (.getX e))
  (def my (.getY e)))

(defn mouse-moved [e]
;  (println "mouseMoved:" e)
  )

(defn mouse-wheel-moved [e]
;  (println "mouseWheelMoved:" e)
  (let [dz (/ (.getWheelRotation e) 2)
        cz (:z @g-camera)]
    (dosync (alter g-camera assoc :z (+ cz dz)))
    (println (:z @g-camera))))


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

(println "=== begin ===")
(go)
(println "=== end ===")