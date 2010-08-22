(ns leiningen.boot
  (:use [clj-layar.core :as main]))

(defn boot 
  [project & args] 
  (main/start-server))
