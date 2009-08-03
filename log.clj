(.delete (File. "C:\\log.log"))
(defn log [& strings]
  (doto (FileWriter. "C:\\log.log" true)
    (.write (apply str strings))
    (.write "\r\n")
    (.close))
  nil)
