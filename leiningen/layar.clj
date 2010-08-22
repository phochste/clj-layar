(ns leiningen.layar
  (:use [clj-layar.core :as main]))

(defn layar
  [project & args] 
  (if-let [file (first args)]
    (main/upload file)
    (println "usage: lein upload <file>")))
