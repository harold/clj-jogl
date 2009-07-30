(ns clj-jogl
  (:import [java.awt Frame]
           [java.awt.event WindowAdapter]
           [com.sun.opengl.util FPSAnimator]
           [javax.media.opengl GLCanvas GLEventListener GL]))

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
        a (int (/ w h))]
    (doto gl
      (.glMatrixMode GL/GL_PROJECTION)
      (.glLoadIdentity)
      (.glFrustum -1 1 (- 0 a) a, 0 10)
      (.glMatrixMode GL/GL_MODELVIEW)
      (.glLoadIdentity))))

(defn get-time [& _] (/ (System/nanoTime) 1000000))
(def t (ref (get-time)))
(def x (ref 1))

(defn gl-display [canvas]
;  (println "gl-display" canvas)
  (let [gl (.getGL canvas)
        dt (- (get-time) @t)]
    (dosync (alter t get-time))
    (dosync (alter x + (/ dt 1000.0)))
    (when (> @x 1) (dosync (alter x (fn [_] -1))))
    (doto gl
      (.glClearColor 0 0 0 0)
      (.glClear (bit-or GL/GL_COLOR_BUFFER_BIT GL/GL_DEPTH_BUFFER_BIT))
      (.glLoadIdentity)
      (.glColor3f 0.5 0.5 1.0)
      (.glBegin GL/GL_TRIANGLES)
      (.glVertex3f      @x   1 0)
      (.glVertex3f      -1  @x 0)
      (.glVertex3f (- 0 @x) -1 0)
      (.glEnd))))

(defn get-gl-app []
  (proxy [GLEventListener] []
    (init [canvas] (gl-init canvas))
    (reshape [canvas x y w h] (gl-reshape canvas x y w h))
    (display [canvas] (gl-display canvas))
    (displayChanged [canvas mode-changed device-changed]
      (println "DisplayChanged:" canvas mode-changed device-changed))))

(defn go []
  (let [frame (Frame. "clj-jogl")
        canvas (GLCanvas.)
        animator (FPSAnimator. canvas 60)]
    (.addGLEventListener canvas (get-gl-app))
    (.start animator)
    (doto frame
      (.addWindowListener (get-window-adapter frame animator))
      (.add canvas)
      (.setSize 640 480)
      (.setIgnoreRepaint true)
      (.show))
    (loop []
      (when (.isVisible frame)
        (recur)))))

(println "=== begin ===")
(go)
(println "=== end ===")