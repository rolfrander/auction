(ns auksjon.test-util
  (:require [auksjon.db.sqlite :as sqlite]
            [auksjon.images.memory :as images.memory])
  (:import (java.io File)))

(defn fresh-store []
  (let [f (File/createTempFile "auksjon-test" ".db")]
    (.deleteOnExit f)
    (sqlite/create-store (str "jdbc:sqlite:" (.getAbsolutePath f)))))

(defn fresh-image-store [] (images.memory/create-store))
