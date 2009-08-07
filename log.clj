(ns log
  (:import (java.io File FileWriter)))

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
