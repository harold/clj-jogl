(ns srg-object)

(defn make-vertex [vertex-tag]
  (let [[x y]   (.split #"\s" (:position (:attrs vertex-tag)))
        [r g b] (.split #"\s" (:color    (:attrs vertex-tag)))]
    {:position {:x (Double. x) :y (Double. y)}
     :color    {:r (Double. r) :g (Double. g) :b (Double. b)}}))

(defn make-polygon [polygon-tag]
  (let [content (:content polygon-tag)]
    (ref {:tess nil
          :verts (map make-vertex (filter #(= :vertex (:tag %)) content))})))

(defn get-polygons [tree]
  (let [content (:content tree)]
    (map make-polygon (filter #(= :polygon (:tag %)) content))))

(defn from-xml [path]
  (let [tree (clojure.xml/parse path)]
    (get-polygons tree)))
