(ns auksjon.handler-test
  (:require [auksjon.handler :as handler]
            [auksjon.test-util :refer [fresh-image-store fresh-store]]
            [clojure.test :refer [deftest is testing]]
            [muuntaja.core :as m]
            [ring.mock.request :as mock]))

(defn- new-app [] (handler/app {:store (fresh-store) :image-store (fresh-image-store) :image-base-url "http://img.test"}))

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
        app (handler/app {:store (fresh-store) :static-dir (.getPath dir)})]
    (doseq [uri ["/admin" "/admin/" "/admin/items/123"]]
      (let [resp (app (mock/request :get uri))]
        (is (= 200 (:status resp)) uri)
        (is (= "<html>admin</html>" (slurp (:body resp))) uri)))
    (is (= 404 (:status (app (mock/request :get "/admin/missing.js")))))))
