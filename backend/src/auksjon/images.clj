(ns auksjon.images
  "Image storage abstraction. Handlers depend only on this protocol;
  implementations live in auksjon.images.* (S3, in-memory for tests).
  Keys are opaque strings like \"3f0c….jpg\".")

(defprotocol ImageStore
  (put-image [store key content-type file] "Stores java.io.File under key.")
  (get-image [store key]
    "Returns {:content-type :content-length :body InputStream} or nil if missing.
    (Not used by the API — clients load images from the public URL.)")
  (delete-image [store key]))
