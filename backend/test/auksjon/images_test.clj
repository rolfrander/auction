(ns auksjon.images-test
  (:require [auksjon.config :as config]
            [auksjon.handler :as handler]
            [auksjon.images :as images]
            [auksjon.test-util :refer [authed fresh-image-store fresh-store test-auth]]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [muuntaja.core :as m]
            [ring.mock.request :as mock])
  (:import (java.io ByteArrayOutputStream)))

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

(deftest parse-env-file-test
  (is (= {"A" "1" "B" "http://x:3900" "C" "q u" "D" "z"}
         (config/parse-env-file "A=1\n# c\nB: http://x:3900 \n\nC=\"q u\"\nD : z\n"))))
