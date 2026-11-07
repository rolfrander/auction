(ns auksjon.main
  (:require [auksjon.config :as config]
            [auksjon.db.sqlite :as sqlite]
            [auksjon.handler :as handler]
            [auksjon.images.s3 :as s3]
            [clojure.string :as str]
            [ring.adapter.jetty :as jetty])
  (:gen-class))

(defn create-store
  ([] (create-store (config/load-env)))
  ([env] (sqlite/create-store (get env "DB_URL" "jdbc:sqlite:auksjon.db"))))

(defn create-image-store
  ([] (create-image-store (config/load-env)))
  ([env] (s3/create-store env)))

(defn image-base-url
  ([] (image-base-url (config/load-env)))
  ([env] (str/replace (or (not-empty (get env "S3_WEB_URL"))
                          (throw (ex-info "Missing config S3_WEB_URL" {:key "S3_WEB_URL"})))
                      #"/+$" "")))

(defn start-server [{:keys [port store image-store image-base-url static-dir join?]}]
  (jetty/run-jetty (handler/app {:store store :image-store image-store
                                 :image-base-url image-base-url :static-dir static-dir})
                   {:port port :host "0.0.0.0" :join? join?}))

(defn -main [& _args]
  (let [env (config/load-env)
        port (parse-long (get env "PORT" "3000"))
        static-dir (get env "STATIC_DIR" "../frontend/dist")]
    (println (str "Listening on http://0.0.0.0:" port " (API docs: /api/docs)"))
    (start-server {:port port :store (create-store env) :image-store (create-image-store env)
                   :image-base-url (image-base-url env)
                   :static-dir static-dir :join? true})))
