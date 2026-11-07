(ns auksjon.test-util
  (:require [auksjon.db.sqlite :as sqlite])
  (:import (java.io File)))

(defn fresh-store []
  (let [f (File/createTempFile "auksjon-test" ".db")]
    (.deleteOnExit f)
    (sqlite/create-store (str "jdbc:sqlite:" (.getAbsolutePath f)))))
