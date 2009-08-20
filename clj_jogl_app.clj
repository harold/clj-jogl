(ns clj-jogl-app
  (:require [clj-jogl-tess :as jtess]
            [srg-object :as srg-object]))

(defn create []
  (let [camera (ref {:x  0 :y  0 :z  -10
                     :tx 0 :ty 0 :tz -10})
        new-app (ref {:mode             :normal
                      :tool             :select
                      :doc              nil
                      :camera           camera
                      :vertex-selection {}})]
    new-app))

(defn set-mode [app mode]
  (dosync (alter app assoc :mode mode)))

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

(defn get-doc [app]
  (:doc @app))

(defn get-cmd-system [app]
  (:cmd-system @app))

(defn get-camera [app]
  (:camera @app))

(defn set-camera-property [app property value]
  (dosync (alter (get-camera app) assoc property value)))

(defn poly-tess [poly]
  (if (= nil (:tess poly))
    (do
      (assoc poly :tess (jtess/tess (:vertices poly))))
    poly))

(defn doc-tess [doc]
  {:polygons (doall (map poly-tess (:polygons doc)))})

(defn tess [app]
  (dosync (alter app assoc :doc (doc-tess (get-doc app)))))

(defn get-tool [app]
  (:tool @app))

(defn set-tool [app tool]
  (dosync (alter app assoc :tool tool)))

(defn get-vertex-distance-pair [[x y] vertex]
  (let [vx (:x (:position vertex))
        vy (:y (:position vertex))
        dx (- x vx)
        dy (- y vy)
        d (+ (* dx dx) (* dy dy))]
    [d vertex]))

(defn select-nearest-vertex [app x y]
  (let [vert-list (mapcat :vertices (:polygons (get-doc app)))
        pair-list (map get-vertex-distance-pair (repeat [x y]) vert-list)
        winner (first (sort-by first pair-list))]
    (dosync (alter app assoc :vertex-selection (second winner)))))

(defn get-vertex-selection [app]
  (:vertex-selection @app))