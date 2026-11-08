(ns auksjon.api.images
  "Image upload (admin). Items reference images by their public URL
  (<image-base-url>/<key>), served directly by S3 without authentication.
  Preferred flow: POST /images/upload-form, then the browser POSTs the file
  straight to S3. POST /images (multipart via the backend) is kept as fallback."
  (:require [auksjon.images :as images]
            [auksjon.schema :as s]
            [clojure.string :as str]
            [reitit.ring.malli :as rm]))

(def ^:private extensions
  {"image/jpeg" "jpg" "image/png" "png" "image/gif" "gif" "image/webp" "webp"
   "image/avif" "avif" "image/heic" "heic" "image/heif" "heif"})

(def max-bytes (* 25 1024 1024))

(defn- normalize-type [content-type]
  (some-> content-type (str/split #";") first str/trim str/lower-case))

(defn upload-form [{:keys [image-store image-base-url parameters]}]
  (let [{:keys [contentType size]} (:body parameters)
        content-type (normalize-type contentType)
        ext (extensions content-type)]
    (cond
      (nil? ext) {:status 415 :body {:error (str "unsupported image type: " contentType)}}
      (> size max-bytes) {:status 413 :body {:error "image too large"}}
      :else (let [key (str (random-uuid) "." ext)
                  {:keys [url fields]} (images/upload-form image-store key content-type max-bytes)]
              {:status 200 :body {:uploadUrl url :fields fields
                                  :url (str image-base-url "/" key)}}))))

(defn upload [{:keys [image-store image-base-url parameters]}]
  (let [{:keys [content-type tempfile size]} (-> parameters :multipart :file)
        content-type (normalize-type content-type)
        ext (extensions content-type)]
    (cond
      (nil? ext) {:status 415 :body {:error (str "unsupported image type: " content-type)}}
      (> size max-bytes) {:status 413 :body {:error "image too large"}}
      :else (let [key (str (random-uuid) "." ext)]
              (images/put-image image-store key content-type tempfile)
              {:status 201 :body {:url (str image-base-url "/" key)}}))))

(def admin-routes
  ["/images" {:swagger {:tags ["admin: images"]}}
   ["/upload-form"
    {:post {:summary "Signed form for uploading one image directly to S3"
            :description "POST `fields` followed by the `file` field as multipart/form-data to `uploadUrl` (204 on success), then store `url` in the item's images."
            :parameters {:body [:map {:closed true}
                                [:contentType :string]
                                [:size [:int {:min 1}]]]}
            :responses {200 {:body [:map
                                    [:uploadUrl :string]
                                    [:fields [:map-of :string :string]]
                                    [:url :string]]}
                        413 {:body s/ErrorBody}
                        415 {:body s/ErrorBody}}
            :handler upload-form}}]
   ["" {:post {:summary "Upload one image via the backend (fallback); returns the URL to put in an item's images"
               :parameters {:multipart [:map [:file rm/temp-file-part]]}
               :responses {201 {:body [:map [:url :string]]}
                           413 {:body s/ErrorBody}
                           415 {:body s/ErrorBody}}
               :handler upload}}]])
