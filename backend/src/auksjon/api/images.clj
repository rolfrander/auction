(ns auksjon.api.images
  "Image upload (admin). Items reference images by the returned public URL
  (<image-base-url>/<key>), served directly by S3 without authentication."
  (:require [auksjon.images :as images]
            [auksjon.schema :as s]
            [clojure.string :as str]
            [reitit.ring.malli :as rm]))

(def ^:private extensions
  {"image/jpeg" "jpg" "image/png" "png" "image/gif" "gif" "image/webp" "webp"
   "image/avif" "avif" "image/heic" "heic" "image/heif" "heif"})

(def max-bytes (* 25 1024 1024))

(defn upload [{:keys [image-store image-base-url parameters]}]
  (let [{:keys [content-type tempfile size]} (-> parameters :multipart :file)
        content-type (some-> content-type (str/split #";") first str/trim str/lower-case)
        ext (extensions content-type)]
    (cond
      (nil? ext) {:status 415 :body {:error (str "unsupported image type: " content-type)}}
      (> size max-bytes) {:status 413 :body {:error "image too large"}}
      :else (let [key (str (random-uuid) "." ext)]
              (images/put-image image-store key content-type tempfile)
              {:status 201 :body {:url (str image-base-url "/" key)}}))))

(def admin-routes
  ["/images" {:swagger {:tags ["admin: images"]}
              :post {:summary "Upload one image; returns the URL to put in an item's images"
                     :parameters {:multipart [:map [:file rm/temp-file-part]]}
                     :responses {201 {:body [:map [:url :string]]}
                                 413 {:body s/ErrorBody}
                                 415 {:body s/ErrorBody}}
                     :handler upload}}])
