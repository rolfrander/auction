(ns auksjon.images-test
  (:require [auksjon.config :as config]
            [auksjon.handler :as handler]
            [auksjon.images :as images]
            [auksjon.images.post-policy :as pp]
            [jsonista.core :as json]
            [auksjon.test-util :refer [authed fresh-image-store fresh-store test-auth]]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [muuntaja.core :as m]
            [ring.mock.request :as mock])
  (:import (java.io ByteArrayOutputStream)
           (java.time Instant)
           (java.util Base64)))

(def ^:private boundary "XyZBoundary123")

(defn- multipart-request [path field filename content-type ^bytes data]
  (let [out (ByteArrayOutputStream.)
        w #(.write out (.getBytes ^String % "UTF-8"))]
    (w (str "--" boundary "\r\n"
            "Content-Disposition: form-data; name=\"" field "\"; filename=\"" filename "\"\r\n"
            "Content-Type: " content-type "\r\n\r\n"))
    (.write out data)
    (w (str "\r\n--" boundary "--\r\n"))
    (-> (mock/request :post path)
        (mock/content-type (str "multipart/form-data; boundary=" boundary))
        (mock/body (.toByteArray out)))))

(defn- slurp-bytes [in]
  (with-open [in in out (ByteArrayOutputStream.)]
    (.transferTo in out)
    (.toByteArray out)))

(deftest upload-and-fetch-test
  (let [images (fresh-image-store)
        app (authed (handler/app {:store (fresh-store) :image-store images :auth test-auth
                                   :image-base-url "http://img.test/auction"}))
        data (byte-array (range 100))]
    (testing "upload"
      (let [resp (app (multipart-request "/api/admin/images" "file" "a.png" "image/png" data))
            {:keys [url]} (m/decode-response-body resp)]
        (is (= 201 (:status resp)))
        (is (re-matches #"http://img\.test/auction/[0-9a-f-]{36}\.png" url))
        (testing "stored in image store"
          (let [r (images/get-image images (last (str/split url #"/")))]
            (is (= "image/png" (:content-type r)))
            (is (= (seq data) (seq (slurp-bytes (:body r)))))))))
    (testing "rejects non-images"
      (is (= 415 (:status (app (multipart-request "/api/admin/images" "file" "a.txt" "text/plain" data))))))
    (testing "missing file"
      (is (= 400 (:status (app (multipart-request "/api/admin/images" "other" "a.png" "image/png" data))))))
))

(deftest upload-form-endpoint-test
  (let [app (authed (handler/app {:store (fresh-store) :image-store (fresh-image-store) :auth test-auth
                                   :image-base-url "http://img.test/auction"}))
        post #(app (-> (mock/request :post "/api/admin/images/upload-form") (mock/json-body %)))]
    (testing "returns signed form and public url"
      (let [resp (post {:contentType "image/JPEG" :size 1000})
            {:keys [uploadUrl fields url]} (m/decode-response-body resp)
            key (last (str/split url #"/"))]
        (is (= 200 (:status resp)))
        (is (re-matches #"http://img\.test/auction/[0-9a-f-]{36}\.jpg" url))
        (is (= "memory://images" uploadUrl))
        (is (= {:key key :Content-Type "image/jpeg"} (select-keys fields [:key :Content-Type])))))
    (testing "rejects non-images" (is (= 415 (:status (post {:contentType "text/plain" :size 10})))))
    (testing "rejects too large" (is (= 413 (:status (post {:contentType "image/png" :size (inc (* 25 1024 1024))})))))
    (testing "rejects unknown keys" (is (= 400 (:status (post {:contentType "image/png" :size 10 :x 1})))))))

(deftest sigv4-test
  (testing "signing key matches AWS's published example"
    (is (= "f4780e2d9f65fa895f9c67b32ce1baf0b0d8a43505a000a1a9e090d414db404d"
           (pp/hex (pp/signing-key "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY" "20120215" "us-east-1" "iam")))))
  (testing "policy signature (reference computed with Python hmac)"
    (is (= "c9de2354d7be660c4e212f9127fe74ca7b5f720b34ab5cb409dfd273fc8ff954"
           (pp/sign "XXXXXXXX-XXXX-XXXXXXX-XXXXXXXXXX" "20190918" "fr-par"
                    (str "eyJleHBpcmF0aW9uIjogIjIwMTktMDktMTlUMDI6MDA6MDAuMDAwWiIsICJjb25kaXRpb25zIjogW3siYnVja2V0IjogIm15YnVja2V0In0sIFsic3RhcnRzLXdpdGgiLCAiJGtleSIsICIiXSwgeyJhY2wiOiAicHVibGljLXJlYWQifSwgeyJ4LWFtei1jcmVkZW50aWFsIjogIlNDV1hYWFhYWFhYWFhYWFhYWFhYLzIwMTkwOTE4L2ZyLXBhci9zMy9hd3M0X3JlcXVlc3QifSwgeyJ4LWFtei1hbGdvcml0aG0iOiAiQVdTNC1ITUFDLVNIQTI1NiJ9LCB7IngtYW16LWRhdGUiOiAiMjAxOTA5MTlUMDAwMDAwWiJ9LCB7InN1Y2Nlc3NfYWN0aW9uX3N0YXR1cyI6ICIyMDQifV19"))))))

(deftest upload-form-policy-test
  (let [{:keys [url fields]} (pp/upload-form {:endpoint "https://s3.fr-par.scw.cloud" :region "fr-par" :bucket "b"
                                              :access-key "SCWKEY" :secret-key "secret"
                                              :now (Instant/parse "2026-11-08T10:00:00Z")}
                                             {:key "k.png" :content-type "image/png" :max-bytes 100
                                              :cache-control "public"})
        policy (json/read-value (String. (.decode (Base64/getDecoder) ^String (fields "policy")) "UTF-8"))
        conds (policy "conditions")]
    (is (= "https://s3.fr-par.scw.cloud/b" url))
    (is (= "2026-11-08T10:10:00.000Z" (policy "expiration")))
    (is (= "SCWKEY/20261108/fr-par/s3/aws4_request" (fields "x-amz-credential")))
    (is (= "20261108T100000Z" (fields "x-amz-date")))
    (is (= (pp/sign "secret" "20261108" "fr-par" (fields "policy")) (fields "x-amz-signature")))
    (testing "every form field except policy/signature/file has a condition"
      (doseq [[k v] (dissoc fields "policy" "x-amz-signature")]
        (is (some #{{k v}} conds) k)))
    (is (some #{{"bucket" "b"}} conds))
    (is (some #{["content-length-range" 1 100]} conds))))

(deftest parse-env-file-test
  (is (= {"A" "1" "B" "http://x:3900" "C" "q u" "D" "z"}
         (config/parse-env-file "A=1\n# c\nB: http://x:3900 \n\nC=\"q u\"\nD : z\n"))))
