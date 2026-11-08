(ns auksjon.images
  "Image storage abstraction. Handlers depend only on this protocol;
  implementations live in auksjon.images.* (S3, in-memory for tests).
  Keys are opaque strings like \"3f0c….jpg\".")

(def cache-control
  "Keys are random and never rewritten, so images can be cached forever."
  "public, max-age=31536000, immutable")

(defprotocol ImageStore
  (put-image [store key content-type file] "Stores java.io.File under key (publicly readable).")
  (upload-form [store key content-type max-bytes]
    "Returns {:url :fields} for a browser multipart POST (fields, then `file`)
    that stores one object of content-type and at most max-bytes under key.")
  (get-image [store key]
    "Returns {:content-type :content-length :body InputStream} or nil if missing.
    (Not used by the API — clients load images from the public URL.)")
  (delete-image [store key]))
