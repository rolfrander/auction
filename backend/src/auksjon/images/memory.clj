(ns auksjon.images.memory
  "In-memory ImageStore (tests / dev without S3)."
  (:require [auksjon.images :as images])
  (:import (java.io ByteArrayInputStream)
           (java.nio.file Files)))

(defrecord MemoryStore [state]
  images/ImageStore
  (put-image [_ key content-type file]
    (swap! state assoc key {:content-type content-type
                            :bytes (Files/readAllBytes (.toPath ^java.io.File file))})
    nil)
  (upload-form [_ key content-type max-bytes]
    {:url "memory://images"
     :fields {"key" key "Content-Type" content-type "max-bytes" (str max-bytes)}})
  (get-image [_ key]
    (when-let [{:keys [content-type ^bytes bytes]} (get @state key)]
      {:content-type content-type
       :content-length (alength bytes)
       :body (ByteArrayInputStream. bytes)}))
  (delete-image [_ key] (swap! state dissoc key) nil))

(defn create-store [] (->MemoryStore (atom {})))
