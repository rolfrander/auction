(ns auction.system 
  (:require
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [ring.adapter.jetty :refer [run-jetty]]
   [auction.app :as app]))
   

(def system
  {:app/config {}
   :app/handler {}
   :app/adapter {:config (ig/ref :app/config)
                 :handler (ig/ref :app/handler)}})
   

(defmethod ig/init-key :app/config [_ _]
  (read-string (slurp (io/resource "config.edn"))))

(defmethod ig/init-key :app/handler [_ config]
  (app/app config))

(defmethod ig/init-key :app/adapter [_ {:keys [config handler]}]
  (run-jetty handler (assoc config :join? false)))
  

(defmethod ig/halt-key! :app/adapter [_ jetty]
  (.stop jetty))

(comment)
  
  