(ns clj-jogl-commands
  (:require [clj-jogl-gl :as jgl])
  (:import (java.io File FileWriter FileReader PushbackReader)))

(defn create-command-system
  "Create a jogl command system"
  [camera]
  (ref { :camera camera :commands [] }))

(defn relative-drag-start
  [cmd-system-ref [x y]]
  (let [camera (:camera @cmd-system-ref)
        world-point (jgl/screen-to-world x
                                         y
                                         (double (- (:z @camera)))
                                         camera)]
    (dosync (alter cmd-system-ref assoc :drag-start [(:x world-point) (:y world-point)]))))


(defn relative-drag [cmd-system-ref cmd]
  (let [[cmd-x cmd-y] (:data cmd)
        cmd-system @cmd-system-ref
        camera (:camera cmd-system)
        [start-x start-y] (:drag-start cmd-system)
        world-point (jgl/screen-to-world cmd-x
                                         cmd-y
                                         (double (- (:z @camera)))
                                         camera)]
    (let [dx (- start-x (:x world-point))
          dy (- (:y world-point) start-y)
          cx (:x @camera)
          cy (:y @camera)]
      (dosync (alter camera assoc :x (+ cx dx)
                     :y (- cy dy))))
    (relative-drag-start cmd-system-ref [cmd-x cmd-y])))

(defn relative-zoom [cmd-system-ref cmd]
  (let [direction (:data cmd)
        camera (:camera @cmd-system-ref)
        dz (* -10 direction)
        cz (:z @camera)]
    (dosync (alter camera assoc :tz (+ cz dz)))))

(defn fire-command
  "Fire off a command.  This will ready the command for execution"
  [cmd-system cmd]
  (let [cmd-type (:type cmd)]
    (cond
      (= cmd-type :relative-drag-start) (relative-drag-start cmd-system (:drag-start cmd))
      (= cmd-type :relative-drag) (relative-drag cmd-system cmd)
      (= cmd-type :relative-zoom) (relative-zoom cmd-system cmd)))
  (dosync (alter cmd-system assoc :commands (conj (:commands @cmd-system) cmd))))

(defn spew-commands
  [cmd-system]
  (with-open [fw (FileWriter. "c:\\commands.txt")]
    (binding [*out* fw]
      (prn (:commands @cmd-system)))))

(defn replay-commands
  [cmd-system]
  (try
   (with-open [fr (FileReader. "c:\\commands.txt")]
     (let [cmds (read (PushbackReader. fr))]
       (doseq [cmd cmds] (fire-command cmd-system cmd))))
   (dosync (alter cmd-system assoc :commands []))
   (catch Exception e (.printStackTrace e))))