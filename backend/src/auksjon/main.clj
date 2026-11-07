(ns auksjon.main
  (:require [auksjon.db.sqlite :as sqlite]
            [auksjon.handler :as handler]
            [ring.adapter.jetty :as jetty])
  (:gen-class))

(defn create-store []
  (sqlite/create-store (or (System/getenv "DB_URL") "jdbc:sqlite:auksjon.db")))

(defn start-server [{:keys [port store static-dir join?]}]
  (jetty/run-jetty (handler/app {:store store :static-dir static-dir})
                   {:port port :host "0.0.0.0" :join? join?}))

(defn -main [& _args]
  (let [port (parse-long (or (System/getenv "PORT") "3000"))
        static-dir (or (System/getenv "STATIC_DIR") "../frontend/dist")]
    (println (str "Listening on http://0.0.0.0:" port " (API docs: /api/docs)"))
    (start-server {:port port :store (create-store) :static-dir static-dir :join? true})))
