(ns build
  "Uberjar build: clojure -T:build uber  ->  target/auksjon.jar"
  (:require [clojure.tools.build.api :as b]))

(def class-dir "target/classes")
(def jar-file "target/auksjon.jar")
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_] (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"] :target-dir class-dir})
  (b/compile-clj {:basis @basis :ns-compile '[auksjon.main] :class-dir class-dir})
  (b/uber {:class-dir class-dir :uber-file jar-file :basis @basis :main 'auksjon.main}))
