(ns auksjon.test-util
  (:require [auksjon.auth :as auth]
            [auksjon.db.sqlite :as sqlite]
            [auksjon.images.memory :as images.memory])
  (:import (java.io File)))

(defn fresh-store []
  (let [f (File/createTempFile "auksjon-test" ".db")]
    (.deleteOnExit f)
    (sqlite/create-store (str "jdbc:sqlite:" (.getAbsolutePath f)))))

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
