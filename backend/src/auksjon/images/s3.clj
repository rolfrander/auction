(ns auksjon.images.s3
  "S3-compatible ImageStore (AWS SDK v2, path-style; Scaleway Object Storage,
  also Garage/MinIO). Objects are written with ACL public-read."
  (:require [auksjon.images :as images]
            [auksjon.images.post-policy :as post-policy]
            [clojure.string :as str])
  (:import (java.io File)
           (java.net URI)
           (software.amazon.awssdk.auth.credentials AwsBasicCredentials StaticCredentialsProvider)
           (software.amazon.awssdk.core ResponseInputStream)
           (software.amazon.awssdk.core.checksums RequestChecksumCalculation ResponseChecksumValidation)
           (software.amazon.awssdk.core.sync RequestBody)
           (software.amazon.awssdk.regions Region)
           (software.amazon.awssdk.services.s3 S3Client S3ClientBuilder)
           (software.amazon.awssdk.services.s3.model CORSConfiguration CORSRule DeleteObjectRequest
                                                     GetObjectRequest GetObjectResponse NoSuchKeyException
                                                     ObjectCannedACL PutBucketCorsRequest PutObjectRequest)))

(set! *warn-on-reflection* true)

(defrecord S3Store [^S3Client client ^String bucket form-opts]
  images/ImageStore
  (put-image [_ key content-type file]
    (let [^PutObjectRequest req (-> (PutObjectRequest/builder)
                                    (.bucket bucket) (.key key) (.contentType content-type)
                                    (.cacheControl images/cache-control)
                                    (.acl ObjectCannedACL/PUBLIC_READ)
                                    .build)]
      (.putObject client req (RequestBody/fromFile ^File file))
      nil))
  (upload-form [_ key content-type max-bytes]
    (post-policy/upload-form form-opts {:key key :content-type content-type :max-bytes max-bytes
                                        :cache-control images/cache-control}))
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
        endpoint (req "S3_ENDPOINT")
        region (or (not-empty (get env "S3_REGION")) "us-east-1")
        ^S3ClientBuilder b (S3Client/builder)]
    (doto b
      (.endpointOverride (URI. endpoint))
      (.region (Region/of region))
      (.credentialsProvider (StaticCredentialsProvider/create
                             (AwsBasicCredentials/create (req "S3_ACCESS_KEY_ID") (req "S3_SECRET_ACCESS_KEY"))))
      (.forcePathStyle true)
      ;; newer SDKs send CRC checksums by default, which S3-compatibles may reject
      (.requestChecksumCalculation RequestChecksumCalculation/WHEN_REQUIRED)
      (.responseChecksumValidation ResponseChecksumValidation/WHEN_REQUIRED))
    (->S3Store (.build b) (req "S3_BUCKET")
               {:endpoint (str/replace endpoint #"/+$" "") :region region :bucket (req "S3_BUCKET")
                :access-key (req "S3_ACCESS_KEY_ID") :secret-key (req "S3_SECRET_ACCESS_KEY")})))

(defn configure-cors!
  "Allows browser POST uploads from `origins` to the bucket. One-time setup per bucket:
  clojure -X auksjon.images.s3/configure-cors! :origins '[\"https://example.com\" \"http://localhost:5173\"]'"
  [{:keys [origins env]}]
  (let [{:keys [^S3Client client bucket]} (create-store (or env ((requiring-resolve 'auksjon.config/load-env))))
        ^CORSRule rule (-> (CORSRule/builder)
                           (.allowedOrigins ^java.util.Collection (vec origins))
                           (.allowedMethods ^java.util.Collection (list "POST" "GET"))
                           (.allowedHeaders ^java.util.Collection (list "*"))
                           (.maxAgeSeconds (int 3600))
                           .build)
        ^CORSConfiguration cors (-> (CORSConfiguration/builder)
                                    (.corsRules ^java.util.Collection (list rule))
                                    .build)
        ^PutBucketCorsRequest req (-> (PutBucketCorsRequest/builder)
                                      (.bucket ^String bucket)
                                      (.corsConfiguration cors)
                                      .build)]
    (.putBucketCors client req)
    (println "CORS set on" bucket "for" (vec origins))))
