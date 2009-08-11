(ns clj-jogl-app
  (:require [clj-jogl-commands :as jcmd]
            [srg-object :as srg-object]))

(defn create []
  (let [camera (ref {:x  0 :y  0 :z  -10
                     :tx 0 :ty 0 :tz -10})]
    (ref {:mode       :normal
          :doc        nil
          :camera     camera
          :cmd-system (jcmd/create-command-system camera)})))

(defn set-mode [app mode]
  (dosync (alter app assoc :mode mode)))

(defn load-doc [app path]
  (dosync (alter app assoc :doc (srg-object/from-xml path))))

(defn get-doc [app]
  (:doc @app))

(defn get-cmd-system [app]
  (:cmd-system @app))

(defn get-camera [app]
  (:camera @app))

(defn set-camera-property [app property value]
  (dosync (alter (get-camera app) assoc property value)))