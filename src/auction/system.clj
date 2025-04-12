(ns auction.system 
  (:require
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [reitit.ring :as r]
   [ring.adapter.jetty :refer [run-jetty]]
   ))

(def system
  {:app/config {}
   :app/handler {}
   :app/adapter {:config (ig/ref :app/config)
                 :handler (ig/ref :app/handler)}
   })

(defmethod ig/init-key :app/config [_ _]
  (read-string (slurp (io/resource "config.edn"))))

(defmethod ig/init-key :app/handler [_ _]
  (r/ring-handler
   (r/router
    ["/ping" (constantly {:status 200 :body "pong"})])
   (r/routes 
    (r/create-resource-handler {:path "/"})
    (r/create-default-handler))))

(defmethod ig/init-key :app/adapter [_ {:keys [config handler]}]
  (run-jetty handler (assoc config :join? false))
  )

(defmethod ig/halt-key! :app/adapter [_ jetty]
  (.stop jetty))

(comment
  
  )