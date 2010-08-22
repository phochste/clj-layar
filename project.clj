(set! *warn-on-reflection* false)
(defproject clj-layar "1.0.0-SNAPSHOT"
  :description "FIXME: write"
  :dev-dependencies [
                 [ring/ring-devel "0.2.0"]
                 [mysql/mysql-connector-java "5.1.6"]
                 [org.danlarkin/clojure-json "1.1"]
                 [clojure-csv/clojure-csv "1.1.0"]
                 [ring/ring-core "0.2.0"]
                 [ring/ring-jetty-adapter "0.2.0"]
                 [clj-html "0.1.0"]
                 [log4j "1.2.15" :exclusions [javax.mail/mail
	                                      javax.jms/jms
	                                      com.sun.jdmk/jmxtools
	                                      com.sun.jmx/jmxri]]]   
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]])
