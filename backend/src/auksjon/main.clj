(ns auksjon.main
  (:require [auksjon.auth :as auth]
            [auksjon.config :as config]
            [auksjon.db.sql :as db.sql]
            [auksjon.handler :as handler]
            [auksjon.images.s3 :as s3]
            [clojure.string :as str]
            [ring.adapter.jetty :as jetty])
  (:gen-class))

(defn create-store
  ([] (create-store (config/load-env)))
  ([env] (db.sql/create-store (get env "DB_URL" "jdbc:sqlite:auksjon.db")
                              {:user (not-empty (get env "DB_USER"))
                               :password (not-empty (get env "DB_PASSWORD"))})))

(defn create-image-store
  ([] (create-image-store (config/load-env)))
  ([env] (s3/create-store env)))

(defn image-base-url
  ([] (image-base-url (config/load-env)))
  ([env] (str/replace (or (not-empty (get env "S3_WEB_URL"))
                          (throw (ex-info "Missing config S3_WEB_URL" {:key "S3_WEB_URL"})))
                      #"/+$" "")))

(defn auth-config
  ([] (auth-config (config/load-env)))
  ([env] (auth/config env)))

(defn start-server [{:keys [port store image-store image-base-url auth static-dir join?]}]
  (jetty/run-jetty (handler/app {:store store :image-store image-store
                                 :image-base-url image-base-url :auth auth :static-dir static-dir})
                   {:port port :host "0.0.0.0" :join? join?}))

(defn- cause-chain
  "One line with the message of each exception in the cause chain; platform log
  viewers (Scaleway console) truncate stack traces before the root cause."
  [^Throwable e]
  (->> (iterate #(.getCause ^Throwable %) e)
       (take-while some?)
       (map #(str (.getName (class %)) ": " (.getMessage ^Throwable %)))
       (str/join " <- ")))

(defn- run [& _args]
  (let [env (config/load-env)
        port (parse-long (get env "PORT" "3000"))
        static-dir (get env "STATIC_DIR" "../frontend/dist")]
    (println (str "Listening on http://0.0.0.0:" port " (API docs: /api/docs)"))
    (start-server {:port port :store (create-store env) :image-store (create-image-store env)
                   :image-base-url (image-base-url env) :auth (auth-config env)
                   :static-dir static-dir :join? true})))

(defn -main [& args]
  (try
    (apply run args)
    (catch Throwable e
      (binding [*out* *err*]
        (println "Startup failed:" (cause-chain e))
        (flush))
      (System/exit 1))))
