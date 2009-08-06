(ns clj-jogl-commands
  (:require [clj-jogl-gl :as jgl])
  (:import (java.io File FileWriter FileReader PushbackReader)))

(defn create-command-system
  "Create a jogl command system"
  [camera]
  (ref { :camera camera :commands [] }))



(defn update-mouse-xy 
  [cmd-system-ref [x y]]
  (let [camera (:camera @cmd-system-ref)
        world-point (jgl/screen-to-world x
					 y
					 (double (- (:z @camera)))
					 camera)]
    (dosync (alter cmd-system-ref assoc :drag-start [(:x world-point) (:y world-point)]))))


(defn handle-mouse-dragged [cmd-system-ref cmd]
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
    (update-mouse-xy cmd-system-ref [cmd-x cmd-y])))

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

(defn fire-command 
  "Fire off a command.  This will ready the command for execution"
  [cmd-system cmd]
  (let [cmd-type (:type cmd)]
    (cond 
      (= cmd-type :relative-drag-start) (update-mouse-xy cmd-system (:drag-start cmd))
      (= cmd-type :relative-drag) (handle-mouse-dragged cmd-system cmd)))
  (dosync (alter cmd-system assoc :commands (conj (:commands @cmd-system) cmd))))
