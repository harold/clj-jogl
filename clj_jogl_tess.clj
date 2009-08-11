(ns clj-jogl-tess
  (:import (javax.media.opengl.glu GLU)
           (javax.media.opengl.glu GLUtessellatorCallbackAdapter)))

(defn add-vertex [list-ref data]
  (let [list       @list-ref
        last-index (- (count list) 1)
        last-entry (last list)
        verts      (:verts last-entry)]
    (dosync (alter list-ref assoc last-index (assoc last-entry :verts (conj verts data))))))

(defn add-begin [list-ref type]
  (dosync (alter list-ref conj {:type type :verts []})))

(defn get-tess-handler [list-ref]
  (proxy [GLUtessellatorCallbackAdapter] []
      (vertex [data] (add-vertex list-ref data))
      (begin [type] (add-begin list-ref type))
      (error [e] (println "Error:" e))
      (combine [coords data weights out-data]
               (println "Combine:")
               (println coords)
               (println data)
               (println weights)
               (println out-data))))

(defn tess [polygon]
  (let [#^GLU glu (GLU.)
        tessellator (.gluNewTess glu)
        tess-list (ref [])
        handler (get-tess-handler tess-list)]
    (doto tessellator
      (.gluTessCallback GLU/GLU_TESS_VERTEX handler)
      (.gluTessCallback GLU/GLU_TESS_BEGIN handler)
      (.gluTessCallback GLU/GLU_TESS_END handler)
      (.gluTessCallback GLU/GLU_TESS_ERROR handler)
      (.gluTessCallback GLU/GLU_TESS_COMBINE handler)
      (.gluTessBeginPolygon nil)
      (.gluTessBeginContour))
    (doseq [vertex polygon]
      (let [vert-array (make-array Double/TYPE 3)]
        (aset vert-array 0 (:x (:position vertex)))
        (aset vert-array 1 (:y (:position vertex)))
        (aset vert-array 2 0)
        (.gluTessNormal glu tessellator 0 0 1)
        (.gluTessVertex glu tessellator vert-array 0 vertex)))
    (.gluTessEndContour tessellator)
    (.gluTessEndPolygon tessellator)
    (.gluDeleteTess glu tessellator)
    @tess-list))
