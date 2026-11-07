(ns user
  (:require [auksjon.handler :as handler]
            [auksjon.main :as main]
            [ring.adapter.jetty :as jetty]))

(defonce server (atom nil))
(defonce store (delay (main/create-store)))

(defn start []
  ;; Rebuild the app on every request so re-evaluated code is picked up without a restart.
  (reset! server (jetty/run-jetty (fn [req] ((handler/app {:store @store}) req))
                                  {:port 3000 :host "0.0.0.0" :join? false}))
  :started)

(defn stop []
  (when-let [s @server] (.stop s) (reset! server nil))
  :stopped)

(defn restart [] (stop) (start))
