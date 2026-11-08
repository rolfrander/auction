(ns auksjon.test-util
  (:require [auksjon.auth :as auth]
            [auksjon.db.sql :as db.sql]
            [next.jdbc :as jdbc]
            [auksjon.images.memory :as images.memory])
  (:import (java.io File)))

(defn fresh-store
  "Empty store: a temp SQLite file, or the Postgres database in env
  TEST_DB_URL (tables are emptied, so don't point it at real data)."
  []
  (if-let [url (System/getenv "TEST_DB_URL")]
    (let [store (db.sql/create-store url)]
      (jdbc/execute! (:ds store) ["TRUNCATE catalog_item, catalog, item"])
      store)
    (let [f (File/createTempFile "auksjon-test" ".db")]
      (.deleteOnExit f)
      (db.sql/create-store (str "jdbc:sqlite:" (.getAbsolutePath f))))))

(defn fresh-image-store [] (images.memory/create-store))

;; Low iteration count keeps tests fast.
(def test-auth {:user "admin" :password-hash (auth/hash-password "secret" 1000)})

(defn session-cookie
  "Cookie header value carrying a valid session for `auth`."
  ([] (session-cookie test-auth))
  ([auth] (str auth/cookie-name "=" (auth/session-token auth))))

(defn authed
  "Wraps a handler so every request carries a valid session cookie."
  [handler]
  (let [cookie (session-cookie)]
    (fn [req] (handler (assoc-in req [:headers "cookie"] cookie)))))
