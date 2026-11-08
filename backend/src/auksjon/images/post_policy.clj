(ns auksjon.images.post-policy
  "SigV4-signed S3 POST policies for direct browser uploads
  (https://www.scaleway.com/en/docs/object-storage/api-cli/post-object/).
  AWS SDK v2 for Java has no presigned-POST helper, hence this."
  (:require [jsonista.core :as json])
  (:import (java.nio.charset StandardCharsets)
           (java.time Instant ZoneOffset)
           (java.time.format DateTimeFormatter)
           (java.util Base64 HexFormat)
           (javax.crypto Mac)
           (javax.crypto.spec SecretKeySpec)))

(set! *warn-on-reflection* true)

(defn- utf8 ^bytes [^String s] (.getBytes s StandardCharsets/UTF_8))

(defn- hmac ^bytes [^bytes k ^String msg]
  (let [mac (Mac/getInstance "HmacSHA256")]
    (.init mac (SecretKeySpec. k "HmacSHA256"))
    (.doFinal mac (utf8 msg))))

(defn hex [^bytes b] (.formatHex (HexFormat/of) b))

(defn signing-key
  "SigV4 key for date-stamp (yyyyMMdd), region and service."
  ^bytes [secret date-stamp region service]
  (-> (utf8 (str "AWS4" secret))
      (hmac date-stamp) (hmac region) (hmac service) (hmac "aws4_request")))

(defn sign
  "Hex signature of a base64-encoded policy."
  [secret date-stamp region base64-policy]
  (hex (hmac (signing-key secret date-stamp region "s3") base64-policy)))

(defn- fmt [^Instant t pattern]
  (.format (.withZone (DateTimeFormatter/ofPattern pattern) ZoneOffset/UTC) t))

(defn upload-form
  "Returns {:url :fields} for a multipart POST of exactly one object under `key`
  with the given content type, at most `max-bytes`, publicly readable.
  The browser sends `fields` followed by the `file` field.
  Required opts: :endpoint :region :bucket :access-key :secret-key.
  Optional: :now (Instant), :expires-in-seconds (default 600)."
  [{:keys [endpoint region bucket access-key secret-key ^Instant now expires-in-seconds]
    :or {expires-in-seconds 600}}
   {:keys [key content-type max-bytes cache-control]}]
  (let [now (or now (Instant/now))
        date-stamp (fmt now "yyyyMMdd")
        fields {"key" key
                "acl" "public-read"
                "Content-Type" content-type
                "Cache-Control" cache-control
                "success_action_status" "204"
                "x-amz-algorithm" "AWS4-HMAC-SHA256"
                "x-amz-credential" (str access-key "/" date-stamp "/" region "/s3/aws4_request")
                "x-amz-date" (fmt now "yyyyMMdd'T'HHmmss'Z'")}
        policy {"expiration" (fmt (.plusSeconds now expires-in-seconds) "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                "conditions" (into [{"bucket" bucket}
                                    ["content-length-range" 1 max-bytes]]
                                   (map (fn [[k v]] {k v}))
                                   fields)}
        b64 (.encodeToString (Base64/getEncoder) (utf8 (json/write-value-as-string policy)))]
    {:url (str endpoint "/" bucket)
     :fields (assoc fields
                    "policy" b64
                    "x-amz-signature" (sign secret-key date-stamp region b64))}))
