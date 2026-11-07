(ns auksjon.auth
  "Login + session cookie for /api/admin/*. The password is stored as a salted
  PBKDF2-HMAC-SHA256 hash: `pbkdf2-sha256$<iterations>$<salt b64>$<hash b64>`.
  Generate one with `clojure -M:hash-password`."
  (:require [clojure.string :as str])
  (:import (java.nio.charset StandardCharsets)
           (java.security MessageDigest SecureRandom)
           (java.util Base64)
           (javax.crypto Mac SecretKeyFactory)
           (javax.crypto.spec PBEKeySpec SecretKeySpec)))

(def ^:private default-iterations 600000) ; OWASP 2023 recommendation for PBKDF2-SHA256
(def ^:private key-bits 256)

(defn- b64 [^bytes bs] (.encodeToString (Base64/getEncoder) bs))
(defn- unb64 [^String s] (.decode (Base64/getDecoder) s))
(defn- utf8 [^String s] (.getBytes s StandardCharsets/UTF_8))

(defn- pbkdf2 [^String password ^bytes salt iterations]
  (-> (SecretKeyFactory/getInstance "PBKDF2WithHmacSHA256")
      (.generateSecret (PBEKeySpec. (.toCharArray password) salt (int iterations) key-bits))
      (.getEncoded)))

(defn hash-password
  ([password] (hash-password password default-iterations))
  ([password iterations]
   (let [salt (byte-array 16)]
     (.nextBytes (SecureRandom.) salt)
     (str "pbkdf2-sha256$" iterations "$" (b64 salt) "$" (b64 (pbkdf2 password salt iterations))))))

(defn parse-hash
  "Returns {:iterations :salt :hash} or throws on a malformed hash string."
  [s]
  (let [[algo iters salt h :as parts] (str/split (or s "") #"\$")]
    (when-not (and (= 4 (count parts)) (= "pbkdf2-sha256" algo) (re-matches #"\d+" iters))
      (throw (ex-info "Malformed password hash, expected pbkdf2-sha256$<iterations>$<salt>$<hash>" {})))
    {:iterations (parse-long iters) :salt (unb64 salt) :hash (unb64 h)}))

(defn verify-password [password hash-str]
  (let [{:keys [iterations salt hash]} (parse-hash hash-str)]
    (MessageDigest/isEqual ^bytes hash ^bytes (pbkdf2 password salt iterations))))

(defn- hmac-b64url [^bytes key ^String msg]
  (let [mac (doto (Mac/getInstance "HmacSHA256") (.init (SecretKeySpec. key "HmacSHA256")))]
    (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) (.doFinal mac (utf8 msg)))))

;; --- sessions ---------------------------------------------------------------
;; Stateless signed cookie: `<expires-epoch-ms>.<HMAC(user "\n" expires)>`.
;; The HMAC key is derived from ADMIN_PASSWORD_HASH, so sessions survive a
;; restart and changing the password logs everyone out.

(def cookie-name "auksjon_session")
(def session-max-age-s (* 30 24 60 60))

(defn- session-key [{:keys [password-hash]}]
  (.digest (MessageDigest/getInstance "SHA-256") (utf8 (str "auksjon-session\n" password-hash))))

(defn session-token
  "Signed session token for the configured user, valid until `expires-ms`."
  ([auth] (session-token auth (+ (System/currentTimeMillis) (* 1000 session-max-age-s))))
  ([{:keys [user] :as auth} expires-ms]
   (str expires-ms "." (hmac-b64url (session-key auth) (str user "\n" expires-ms)))))

(defn valid-token? [{:keys [user] :as auth} token]
  (boolean
   (when-let [[_ exp sig] (some->> token (re-matches #"(\d{1,15})\.([A-Za-z0-9_-]+)"))]
     (and (< (System/currentTimeMillis) (parse-long exp))
          (MessageDigest/isEqual (utf8 sig) (utf8 (hmac-b64url (session-key auth) (str user "\n" exp))))))))

(defn- request-cookie [request name]
  (some (fn [part]
          (let [[k v] (str/split (str/trim part) #"=" 2)]
            (when (= k name) v)))
        (some-> (get-in request [:headers "cookie"]) (str/split #";"))))

(defn authenticated? [auth request]
  (valid-token? auth (request-cookie request cookie-name)))

(defn- secure? [request]
  (or (= :https (:scheme request))
      (= "https" (get-in request [:headers "x-forwarded-proto"]))))

(defn- set-cookie [request value max-age]
  (str cookie-name "=" value "; Path=/api; Max-Age=" max-age "; HttpOnly; SameSite=Strict"
       (when (secure? request) "; Secure")))

(defn check-credentials
  "True if username/password match the configured admin."
  [{:keys [user password-hash]} username password]
  (and (MessageDigest/isEqual (utf8 username) (utf8 user))
       (verify-password password password-hash)))

(defn login-cookie [auth request] (set-cookie request (session-token auth) session-max-age-s))
(defn logout-cookie [request] (set-cookie request "" 0))

;; --- middleware -------------------------------------------------------------

(defn admin-path? [uri]
  (or (= uri "/api/admin") (str/starts-with? uri "/api/admin/")))

(def ^:private unauthorized
  {:status 401
   :headers {"Content-Type" "application/json; charset=utf-8"}
   :body "{\"error\":\"unauthorized\"}"})

(defn wrap-admin-auth
  "Requires a valid session cookie for /api/admin/*. Runs before body parsing
  so unauthenticated uploads are rejected without being read."
  [handler auth]
  (parse-hash (:password-hash auth)) ; fail fast on bad config
  (fn [request]
    (if (and (admin-path? (:uri request)) (not (authenticated? auth request)))
      unauthorized
      (handler request))))

(defn config
  "Auth config from env: ADMIN_USER and ADMIN_PASSWORD_HASH (both required)."
  [env]
  (let [user (not-empty (get env "ADMIN_USER"))
        h (not-empty (get env "ADMIN_PASSWORD_HASH"))]
    (when-not (and user h)
      (throw (ex-info "Missing config ADMIN_USER / ADMIN_PASSWORD_HASH (generate the hash with `clojure -M:hash-password`)" {})))
    (parse-hash h)
    {:user user :password-hash h}))

(defn -main
  "Prompts for a password and prints its hash for ADMIN_PASSWORD_HASH."
  [& _]
  (let [pw (if-let [c (System/console)]
             (let [a (String. (.readPassword c "Password: " (object-array 0)))
                   b (String. (.readPassword c "Repeat: " (object-array 0)))]
               (when-not (= a b) (binding [*out* *err*] (println "Passwords differ")) (System/exit 1))
               a)
             (read-line))]
    (when (str/blank? pw) (binding [*out* *err*] (println "Empty password")) (System/exit 1))
    (println (str "ADMIN_PASSWORD_HASH=" (hash-password pw)))))
