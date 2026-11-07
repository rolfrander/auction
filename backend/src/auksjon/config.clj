(ns auksjon.config
  "Configuration from environment variables, falling back to a local .env file.
  .env lines may be `KEY=value` or `KEY: value`; `#` starts a comment."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn parse-env-file [s]
  (into {}
        (keep (fn [line]
                (when-let [[_ k v] (re-matches #"\s*([A-Za-z_][A-Za-z0-9_]*)\s*[=:]\s*(.*?)\s*" line)]
                  [k (str/replace v #"^(['\"])(.*)\1$" "$2")])))
        (remove #(re-matches #"\s*(#.*)?" %) (str/split-lines s))))

(defn load-env
  "Merged map of .env (if present) and System env; real env vars win."
  ([] (load-env ".env"))
  ([path]
   (merge (let [f (io/file path)] (when (.exists f) (parse-env-file (slurp f))))
          (into {} (System/getenv)))))
