(ns clj-layar.core
  (:use [clojure.contrib.sql :as sql])
  (:use [clojure-csv.core :as csv])
  (:use [org.danlarkin.json :as json])
  (:use [clj-html.core :as html])
  (:use [clojure.contrib.logging :as log])
  (:use [ring.adapter.jetty])
  (:use [ring.middleware reload stacktrace file file-info params multipart-params]))

(def +jetty-port+ 8080)

(def +db-name+ "layar")
(def +db-user+ "layar")
(def +db-pass+ "mysecret")

(def +layer+ "demo_layer")

(def +poi-schema+ [:attribution :title :lat :lon :imageURL
                   :line4 :line3 :line2 :type :actions
                   :dimension :alt :relativeAlt :transform
                   :object :distance])

;; Database creation functions...
(defn make-db 
  "Returns a map defining the database"
  [db-name db-user db-pass] 
          {
          :classname    "com.mysql.jdbc.Driver"
          :subprotocol  "mysql"  
          :subname      (str "//localhost:3306/" db-name)
          :user         db-user 
          :password     db-pass
          })

(defn- create-poi-table
  "Create a POI table in your local MySQL database"
  []
  (sql/create-table
    :POI_Table
    [:id :integer "PRIMARY KEY" "AUTO_INCREMENT"]
    [:attribution "varchar(50) default NULL"]
    [:title       "varchar(50) default NULL"]
    [:lat         "decimal(20,10) default NULL"]
    [:lon         "decimal(20,10) default NULL"]
    [:imageURL    "varchar(255) default NULL"]
    [:line4       "varchar(50) default NULL"]
    [:line3       "varchar(50) default NULL"]
    [:line2       "varchar(50) default NULL"]
    [:type        "int(11) NOT NULL default '0'"]
    [:actions     "varchar(50) default NULL"]
    [:dimension   "int(1) NOT NULL default '1'"]
    [:alt         "int(10) default NULL"]
    [:relativeAlt "int(10) default NULL"]
    [:transform   "int(10) default NULL"]
    [:object      "int(10) default NULL"]
    [:distance    "decimal(20,10) default NULL"]
    ))

(defn- drop-poi-table
   "Drop the POI table from your MySQL database"
   []
   (try
    (sql/do-commands "DROP TABLE IF EXISTS POI_Table")
    (catch Exception _)))

(defn db-init 
  "Initialize the database by creating all the tables"
  [db]
  (sql/with-connection db
    (drop-poi-table)
    (create-poi-table)
    ))

(defn- empty-nil
  "Converts all empty strings in a col to nil values"
  [col]
  (for [s col] (if (empty? s) nil s)))

(defn- header
  "Returns header for local or remote CSV file" 
  [uri]
  (first (csv/parse-csv (slurp uri))))

(defn- data 
  "Return data for local or remote CSV file"
  [uri]
  (rest (csv/parse-csv (slurp uri))))

(defn- insert-entry
  "Insert a POI sequence"
  [col]
  (sql/insert-values :POI_Table +poi-schema+ (empty-nil col)))

(defn- delete-all
  "Delete all POI entries"
  []
  (sql/delete-rows :POI_Table ["id>0"]))


(defn db-load-file
  "Upload a CSV into the database"
  [db filename]
  (sql/with-connection db
    (sql/transaction
      (do
        ;; Delete the old entries
        (db-init db)
        ;; Insert the new entries
        (doseq [vals (data filename)]
          (when (= (count vals) (count +poi-schema+))
            (insert-entry vals)))))))

;; Database selection functions...
(defn- clean-lan-lot
  [col]
  (let [lat (:lat col)
        lon (:lon col)
        res (into {} col)]
    (-> res 
        (assoc :lat (int (* 1000000 lat)))
        (assoc :lon (int (* 1000000 lon)))
        (assoc :actions []))))

(defn db-get-pois
  "Search for POI entries in the database"
  [db lat lon radius] 
  (sql/with-connection db
    (sql/with-query-results rs [
      (str
        "SELECT *, (((acos(sin((? * pi() / 180)) * sin((lat * pi() / 180)) + "
        "             cos((? * pi() / 180)) * cos((lat * pi() / 180))  * "
        "             cos((?  - lon) * pi() / 180)) "
        "            ) * 180 / pi()) * 60 * 1.1515 * 1.609344 * 1000) as poi_distance "
        "        FROM POI_Table "
        "        HAVING poi_distance < ? "
        "        ORDER BY poi_distance ASC "
        "        LIMIT 0, 50 ")
        lat lat lon radius]
            (doall (map clean-lan-lot rs)))))

;; Web code...
(defn- parse-int
  [str]
  (if (integer? str)
    str
  (try
    (Integer/parseInt str) 
    (catch NumberFormatException nfe 0))))

(defn- parse-double
  [str]
  (if (float? str)
    str
  (try
    (Double/parseDouble str) 
    (catch NumberFormatException nfe 0))))

(defn- parse-query
  "Return map of keyword->param values geven a HTTP request."
  [req]
  (let [params (:query-params req)
        query (zipmap (map keyword (keys params)) (vals params))]
    (-> query
        (assoc :lat    (parse-double (:lat query)))
        (assoc :lon    (parse-double (:lon query)))
        (assoc :radius (parse-int (:radius query))))))

(defn do-poi
  [req]
  (let [
        q      (parse-query req)
        lat    (:lat q)
        lon    (:lon q)
        radius (:radius q)
        db     (make-db +db-name+ +db-user+ +db-pass+)
        pois   (db-get-pois db lat lon radius)]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body (json/encode-to-str {
              "layer"       +layer+
              "hotspots"    pois
              "errorCode"   0
              "errorString" "ok"
            })
     }))

(defn do-ref
  [req]
  (let [
        q      (parse-query req)
        file   (:ref q)
       ]
    (do
      (db-load-file (make-db +db-name+ +db-user+ +db-pass+) file)
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body (html/html
          [:html
           [:body
            [:h1 "Done"]
           ]
          ])
         })))

(defn do-upload
  [req]
  (let [file-params ((:params req) "file")
        file (:tempfile file-params)
        ]
    (do
      (db-load-file (make-db +db-name+ +db-user+ +db-pass+) file)
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body (html/html
          [:html
           [:body
            [:h1 "Done"]
           ]
          ])
         })))

(defn do-index
  [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (html/html
           [:html 
            [:body 
              [:h1 "Layar POI's"]
              [:h2 "Query"]
              [:form {:method "GET" :action "poi"}
                [:label "LAYER NAME: "] [:input {:type "text" :name "layerName"}] [:br]
                [:label "LAT: "]        [:input {:type "text" :name "lat"}] [:br]
                [:label "LON: "]        [:input {:type "text" :name "lon"}] [:br]
                [:label "RADIUS: "]     [:input {:type "text" :name "radius"}] 
                [:input {:type "submit"}]
              ]
              [:h2 "Upload"]
              [:form {:method "POST" :action "upload" :enctype "multipart/form-data"}
                [:label "FILE: "] [:input {:type "file" :name "file"}]
                [:input {:type "submit"}]
              ]
              [:form {:method "POST" :action "upload-ref"}
                [:label "URL: "] [:input {:type "text" :name "ref" :size "80"}]
                [:input {:type "submit"}]
              ]
            ] 
           ]
           )
   })

(defn- handler
  "Dispatcher for incoming POI requests"
  [req]
  (log/info (str (:uri req) (:query-params req))) 
  (cond
    (= (:uri req) "/")
       (do-index req)
    (= (:uri req) "/poi")
       (do-poi req)
    (= (:uri req) "/upload")
       (do-upload req)
    (= (:uri req) "/ref")
       (do-ref req)))

(def app
  (-> #'handler
    (wrap-file "public")
    (wrap-file-info)
    (wrap-params)
    (wrap-multipart-params)
    (wrap-reload '(clj-layar.core))
    (wrap-stacktrace)))


(defn start-server 
  "Start a Jetty server on port 8080 listening for POI requests"
  []
  (run-jetty #'app {:port +jetty-port+ :join? true}))

(defn upload 
  "Upload a csv file containing POI's into the database"
  ([] (upload "./test.csv"))
  ([file] (db-load-file (make-db +db-name+ +db-user+ +db-pass+) file)))

(defn demo-query
  []
  (db-get-pois (make-db +db-name+ +db-user+ +db-pass+) 51.219975 3.229208 2000))
