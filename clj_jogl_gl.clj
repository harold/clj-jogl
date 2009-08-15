(ns clj-jogl-gl
  (:import [javax.media.opengl GL]
           [javax.media.opengl.glu GLU]))

(defn screen-to-world
  "Take screen points and the camera and return a map
of global space x y at the distance you passed in for z
x - screen space x
y - screen space y
z - global space desired output distance from camera
camera - g-camera"
  [x y z camera-ref]
  (let [#^GLU glu (GLU.)
        #^GL  gl  (GLU/getCurrentGL)
        viewport   (make-array Integer/TYPE 4)
        modelview  (make-array Double/TYPE  16)
        projection (make-array Double/TYPE  16)
        world-coords-near (make-array Double/TYPE 4)
        world-coords-far  (make-array Double/TYPE 4)
        camera @camera-ref]
    (doto gl
      (.glMatrixMode GL/GL_MODELVIEW)
      (.glLoadIdentity)
      (.glPushMatrix)
      (.glTranslatef (- (:x camera))
                     (- (:y camera))
                     (:z camera))
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
              (/ (- z 1) 999.0)))
     :y (+ (nth world-coords-near 1)
           (* (- (nth world-coords-far 1)
                 (nth world-coords-near 1))
              (/ (- z 1) 999.0)))}))
