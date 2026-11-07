(ns auksjon.images.s3
  "S3-compatible ImageStore (AWS SDK v2, path-style; works with Garage/MinIO)."
  (:require [auksjon.images :as images])
  (:import (java.io File)
           (java.net URI)
           (software.amazon.awssdk.auth.credentials AwsBasicCredentials StaticCredentialsProvider)
           (software.amazon.awssdk.core ResponseInputStream)
           (software.amazon.awssdk.core.checksums RequestChecksumCalculation ResponseChecksumValidation)
           (software.amazon.awssdk.core.sync RequestBody)
           (software.amazon.awssdk.regions Region)
           (software.amazon.awssdk.services.s3 S3Client S3ClientBuilder)
           (software.amazon.awssdk.services.s3.model DeleteObjectRequest GetObjectRequest GetObjectResponse
                                                     NoSuchKeyException PutObjectRequest)))

(set! *warn-on-reflection* true)

(defrecord S3Store [^S3Client client ^String bucket]
  images/ImageStore
  (put-image [_ key content-type file]
    (let [^PutObjectRequest req (-> (PutObjectRequest/builder)
                                    (.bucket bucket) (.key key) (.contentType content-type) .build)]
      (.putObject client req (RequestBody/fromFile ^File file))
      nil))
  (get-image [_ key]
    (let [^GetObjectRequest req (-> (GetObjectRequest/builder) (.bucket bucket) (.key key) .build)]
      (try
        (let [^ResponseInputStream in (.getObject client req)
              ^GetObjectResponse resp (.response in)]
          {:content-type (.contentType resp)
           :content-length (.contentLength resp)
           :body in})
        (catch NoSuchKeyException _ nil))))
  (delete-image [_ key]
    (let [^DeleteObjectRequest req (-> (DeleteObjectRequest/builder) (.bucket bucket) (.key key) .build)]
      (.deleteObject client req)
      nil)))

(defn create-store
  "Config keys (strings, as in env): S3_ENDPOINT S3_REGION S3_BUCKET
  S3_ACCESS_KEY_ID S3_SECRET_ACCESS_KEY."
  [env]
  (let [req #(or (not-empty (get env %)) (throw (ex-info (str "Missing config " %) {:key %})))
        ^S3ClientBuilder b (S3Client/builder)]
    (doto b
      (.endpointOverride (URI. (req "S3_ENDPOINT")))
      (.region (Region/of (or (not-empty (get env "S3_REGION")) "us-east-1")))
      (.credentialsProvider (StaticCredentialsProvider/create
                             (AwsBasicCredentials/create (req "S3_ACCESS_KEY_ID") (req "S3_SECRET_ACCESS_KEY"))))
      (.forcePathStyle true)
      ;; newer SDKs send CRC checksums by default, which S3-compatibles may reject
      (.requestChecksumCalculation RequestChecksumCalculation/WHEN_REQUIRED)
      (.responseChecksumValidation ResponseChecksumValidation/WHEN_REQUIRED))
    (->S3Store (.build b) (req "S3_BUCKET"))))
