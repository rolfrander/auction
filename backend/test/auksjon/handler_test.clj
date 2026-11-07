(ns auksjon.handler-test
  (:require [auksjon.auth :as auth]
            [auksjon.handler :as handler]
            [auksjon.test-util :refer [authed fresh-image-store fresh-store session-cookie test-auth]]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [muuntaja.core :as m]
            [ring.mock.request :as mock]))

(defn- raw-app [] (handler/app {:store (fresh-store) :image-store (fresh-image-store)
                                :image-base-url "http://img.test" :auth test-auth}))

(defn- new-app [] (authed (raw-app)))

(defn- call
  ([app method path] (call app method path nil))
  ([app method path body]
   (let [resp (app (cond-> (mock/request method path)
                     (some? body) (mock/json-body body)))]
     {:status (:status resp)
      :body (when (get-in resp [:headers "Content-Type"])
              (m/decode-response-body resp))})))

(defn- create-item! [app title]
  (:body (call app :post "/api/admin/items" {:title title})))

(defn- create-catalog! [app body]
  (:body (call app :post "/api/admin/catalogs" body)))

(deftest health-test
  (is (= {:status 200 :body {:status "ok"}} (call (new-app) :get "/api/health"))))

(deftest not-found-test
  (is (= 404 (:status (call (new-app) :get "/api/nope")))))

(defn- set-cookie [resp] (get-in resp [:headers "Set-Cookie"]))

(defn- cookie-pair
  "name=value part of a Set-Cookie header, ready to send back as Cookie."
  [set-cookie]
  (first (str/split set-cookie #";")))

(deftest admin-auth-test
  (let [app (raw-app)
        req (fn [method path cookie]
              (app (cond-> (mock/request method path)
                     cookie (mock/header "Cookie" cookie))))]
    (testing "admin routes require a session"
      (doseq [[method path] [[:get "/api/admin/items"] [:get "/api/admin/catalogs"]
                             [:delete "/api/admin/items"] [:post "/api/admin/images"]
                             [:get "/api/admin/session"] [:get "/api/admin"]]]
        (let [resp (req method path nil)]
          (is (= 401 (:status resp)) path)
          (is (nil? (get-in resp [:headers "WWW-Authenticate"])) "no browser basic-auth prompt"))))
    (testing "invalid cookies"
      (doseq [c ["auksjon_session=garbage"
                 "auksjon_session=99999999999999.AAAA"
                 (str "auksjon_session=" (auth/session-token test-auth (- (System/currentTimeMillis) 1000)))
                 (str "auksjon_session=" (auth/session-token (assoc test-auth :user "root")))
                 (str "auksjon_session=" (auth/session-token (assoc test-auth :password-hash (auth/hash-password "other" 1000))))
                 (str "other=" (auth/session-token test-auth))]]
        (is (= 401 (:status (req :get "/api/admin/items" c))) c)))
    (testing "valid cookie, also among other cookies"
      (is (= 200 (:status (req :get "/api/admin/items" (session-cookie)))))
      (is (= 200 (:status (req :get "/api/admin/items" (str "a=b; " (session-cookie) "; c=d"))))))
    (testing "public routes stay open"
      (is (= 200 (:status (req :get "/api/health" nil))))
      (is (= 200 (:status (req :get "/api/catalogs" nil))))
      (is (= 200 (:status (req :get "/api/swagger.json" nil)))))))

(deftest login-test
  (let [app (raw-app)
        login (fn [body] (app (-> (mock/request :post "/api/login") (mock/json-body body))))]
    (testing "bad credentials"
      (doseq [body [{:username "admin" :password "wrong"}
                    {:username "root" :password "secret"}]]
        (let [resp (login body)]
          (is (= 401 (:status resp)))
          (is (nil? (set-cookie resp)))))
      (is (= 400 (:status (login {:username "admin"}))))
      (is (= 400 (:status (login {:username "admin" :password "secret" :extra 1})))))
    (testing "login sets a cookie that authorizes admin routes"
      (let [resp (login {:username "admin" :password "secret"})
            sc (set-cookie resp)]
        (is (= 200 (:status resp)))
        (is (= {:username "admin"} (m/decode-response-body resp)))
        (is (re-find #"HttpOnly" sc))
        (is (re-find #"SameSite=Strict" sc))
        (is (re-find #"Path=/api" sc))
        (is (not (re-find #"Secure" sc)) "plain http")
        (let [s (app (-> (mock/request :get "/api/admin/session") (mock/header "Cookie" (cookie-pair sc))))]
          (is (= 200 (:status s)))
          (is (= {:username "admin"} (m/decode-response-body s))))))
    (testing "Secure behind https proxy"
      (is (re-find #"Secure" (set-cookie (app (-> (mock/request :post "/api/login")
                                                  (mock/json-body {:username "admin" :password "secret"})
                                                  (mock/header "X-Forwarded-Proto" "https")))))))
    (testing "logout clears the cookie"
      (let [resp (app (mock/request :post "/api/logout"))]
        (is (= 204 (:status resp)))
        (is (re-find #"^auksjon_session=;.*Max-Age=0" (set-cookie resp)))))))

(deftest auth-config-test
  (is (thrown? Exception (auth/config {})))
  (is (thrown? Exception (auth/config {"ADMIN_USER" "a" "ADMIN_PASSWORD_HASH" "plaintext"})))
  (let [h (auth/hash-password "pw" 1000)]
    (is (= {:user "a" :password-hash h} (auth/config {"ADMIN_USER" "a" "ADMIN_PASSWORD_HASH" h})))
    (is (auth/verify-password "pw" h))
    (is (not (auth/verify-password "pW" h)))
    (is (not= h (auth/hash-password "pw" 1000)) "salted")))

(deftest swagger-test
  (let [{:keys [status body]} (call (new-app) :get "/api/swagger.json")]
    (is (= 200 status))
    (is (contains? (:paths body) (keyword "/api/admin/items/{id}")))))

(deftest items-test
  (let [app (new-app)]
    (testing "create"
      (let [{:keys [status body]} (call app :post "/api/admin/items"
                                        {:title "Vase" :description "Blå" :priceLow 100
                                         :priceHigh 300 :startingPrice 50
                                         :images ["a.jpg"]})]
        (is (= 201 status))
        (is (= "Vase" (:title body)))
        (is (string? (:id body)))
        (let [id (:id body)]
          (testing "get"
            (is (= body (:body (call app :get (str "/api/admin/items/" id))))))
          (testing "patch"
            (let [r (call app :patch (str "/api/admin/items/" id) {:priceHigh 400})]
              (is (= 200 (:status r)))
              (is (= [400 "Vase"] ((juxt :priceHigh :title) (:body r))))))
          (testing "list"
            (is (= [id] (map :id (:body (call app :get "/api/admin/items"))))))
          (testing "delete"
            (is (= 204 (:status (call app :delete (str "/api/admin/items/" id)))))
            (is (= 404 (:status (call app :get (str "/api/admin/items/" id)))))
            (is (= 404 (:status (call app :delete (str "/api/admin/items/" id)))))))))
    (testing "validation"
      (is (= 400 (:status (call app :post "/api/admin/items" {:description "no title"}))))
      (is (= 400 (:status (call app :post "/api/admin/items" {:title "x" :priceLow -1}))))
      (is (= 400 (:status (call app :post "/api/admin/items" {:title "x" :bogus 1}))))
      (is (= 400 (:status (call app :get "/api/admin/items/not-a-uuid"))))
      (is (= 404 (:status (call app :patch (str "/api/admin/items/" (random-uuid)) {:title "x"})))))
    (testing "delete all"
      (create-item! app "a")
      (create-item! app "b")
      (is (= {:status 200 :body {:deleted 2}} (call app :delete "/api/admin/items")))
      (is (= [] (:body (call app :get "/api/admin/items")))))))

(deftest catalogs-admin-test
  (let [app (new-app)
        {:keys [id] :as c} (create-catalog! app {:name "Høst" :date "2026-11-01"})
        path (str "/api/admin/catalogs/" id)]
    (is (= {:name "Høst" :date "2026-11-01" :visible false} (dissoc c :id)))
    (is (= c (:body (call app :get path))))
    (is (= true (:visible (:body (call app :patch path {:visible true})))))
    (is (= [id] (map :id (:body (call app :get "/api/admin/catalogs")))))
    (is (= 400 (:status (call app :post "/api/admin/catalogs" {:name "x" :date "1. nov"}))))
    (is (= 204 (:status (call app :delete path))))
    (is (= 404 (:status (call app :get path))))))

(deftest catalog-items-test
  (let [app (new-app)
        {cid :id} (create-catalog! app {:name "Høst"})
        [a b c] (map #(:id (create-item! app %)) ["a" "b" "c"])
        items-path (str "/api/admin/catalogs/" cid "/items")
        titles #(map :title (:body (call app :get items-path)))]
    (testing "add"
      (is (= 200 (:status (call app :put (str items-path "/" a)))))
      (call app :put (str items-path "/" b))
      (call app :put (str items-path "/" c "?position=0"))
      (is (= ["c" "a" "b"] (titles)))
      (is (= [cid] (:catalogs (:body (call app :get (str "/api/admin/items/" a)))))))
    (testing "reorder"
      (let [r (call app :put items-path [b a c])]
        (is (= 200 (:status r)))
        (is (= ["b" "a" "c"] (map :title (:body r)))))
      (is (= 400 (:status (call app :put items-path [a (str (random-uuid))])))))
    (testing "remove"
      (is (= 204 (:status (call app :delete (str items-path "/" a)))))
      (is (= 404 (:status (call app :delete (str items-path "/" a)))))
      (is (= ["b" "c"] (titles))))
    (testing "unknown ids"
      (is (= 404 (:status (call app :put (str items-path "/" (random-uuid))))))
      (is (= 404 (:status (call app :get (str "/api/admin/catalogs/" (random-uuid) "/items"))))))))

(deftest public-test
  (let [app (new-app)
        visible (create-catalog! app {:name "Synlig" :visible true})
        hidden (create-catalog! app {:name "Skjult"})
        item (create-item! app "Vase")]
    (doseq [c [visible hidden]]
      (call app :put (str "/api/admin/catalogs/" (:id c) "/items/" (:id item))))
    (is (= ["Synlig"] (map :name (:body (call app :get "/api/catalogs")))))
    (is (= ["Vase"] (map :title (:body (call app :get (str "/api/catalogs/" (:id visible) "/items"))))))
    (is (= 404 (:status (call app :get (str "/api/catalogs/" (:id hidden) "/items")))))
    (is (= 404 (:status (call app :get (str "/api/catalogs/" (random-uuid) "/items")))))))

(deftest admin-spa-fallback-test
  (let [dir (doto (java.io.File. (System/getProperty "java.io.tmpdir") (str "auksjon-static-" (random-uuid)))
              (.mkdirs))
        _ (.mkdirs (java.io.File. dir "admin"))
        _ (spit (java.io.File. dir "admin/index.html") "<html>admin</html>")
        app (handler/app {:store (fresh-store) :auth test-auth :static-dir (.getPath dir)})]
    (doseq [uri ["/admin" "/admin/" "/admin/items/123"]]
      (let [resp (app (mock/request :get uri))]
        (is (= 200 (:status resp)) uri)
        (is (= "<html>admin</html>" (slurp (:body resp))) uri)))
    (is (= 404 (:status (app (mock/request :get "/admin/missing.js")))))))
