(ns auction.app
  (:require [reitit.ring :as r]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
            [muuntaja.core :as m]
            [reitit.coercion.malli]
            [clojure.tools.logging :as log]))

(defn copy-recursive [m]
  (cond (nil? m) nil
        (number? m) m
        (map? m) (reduce-kv (fn [m k v] (assoc m k (copy-recursive v))) {} m)
        (seq? m) (map copy-recursive m)
        (vector? m) (mapv copy-recursive m)
        :else    (str m)))

(defn echo-handler [request]
  (log/info "echo-handler" (:parameters request))
  {:status 200 :body  (copy-recursive request)})

(def db (atom {}))

(def NewAuctionObject [:map
                       [:title string?]
                       [:description {:optional true} string?]
                       [:price {:optional true} int?]])

(def AuctionObject (conj NewAuctionObject 
                         [:id string?]
                         [:number int?]
                         [:images [:vector string?]]))

(defn create-object! [o]
  (let [id (str (java.util.UUID/randomUUID))
        o (assoc o :id id)]
    (swap! db assoc id o)
    {:id id}))

(defn update-object [id o]
  (swap! db (fn [db]
              (if (contains? db id)
                (update db id merge o)
                nil
                ))))

(defn get-object [id]
  (get @db id))

(defn wrap-handler [handler parameter-mappings]
  (fn [request]
    (let [input-parameters (:parameters request)
          parameters (map #(get-in input-parameters %) parameter-mappings)
          result (apply handler parameters)]
      (log/debug "calling" handler "with parameters" parameters)
      (case  (nil? result) {:status 404}
             (= result "") {:status 204}
             :else         {:status 200 :body result}))))

(defn app [_config]
  (r/ring-handler
   (r/router
    [["/api" {:swagger {:produces ["application/json"]
                        :consumes ["application/json"]}
              :responses {401 {:description "user not authenticated"}
                          403 {:description "does not have permission to create object"}}}
      ["/ping" {:get (constantly {:status 200, :body "ping"})}]
      ["/pong" {:post (constantly {:status 200, :body "pong"})}]
      ["/object" {:post {:summary "create new auction object" 
                         :parameters {:body NewAuctionObject}
                         :responses {200 {:description "response object describing created resource"}}
                         :handler (wrap-handler create-object! [[:body]])}}]
      ["/object/:id" {:get {:summary "retrieve object by id"
                            :parameters {:path {:id string?}}
                            :responses {200 {:description "returns auction object"}}
                            :handler (wrap-handler get-object [[:path :id]])}
                      :patch {:summary "update description of object"
                              :parameters {:path {:id string?}
                                           :body AuctionObject}
                              :responses {204 {:description "update ok"}}
                              :handler (wrap-handler update-object [[:path :id] [:body]])}}]]
     ["" {:no-doc true}
      ["/swagger.json" {:get (swagger/create-swagger-handler)}]
      ["/api-docs/*" {:get (swagger-ui/create-swagger-ui-handler)}]
      ]]
    {:data {:coercion reitit.coercion.malli/coercion
            :muuntaja
            (m/create (assoc-in m/default-options [:formats "application/json" :encoder-opts] {:do-not-fail-on-empty-beans true}))
            :middleware [swagger/swagger-feature
                         muuntaja/format-middleware
                         
                         ;; query-params & form-params
                         ;parameters/parameters-middleware
                         ;; content-negotiation
                         ;muuntaja/format-negotiate-middleware
                         ;; encoding response body
                         ;muuntaja/format-response-middleware
                         ;; exception handling
                         ;exception/exception-middleware
                         ;; decoding request body
                         ;muuntaja/format-request-middleware
                         ;; coercing response bodys
                         ;coercion/coerce-response-middleware
                         ;; coercing request parameters
                         coercion/coerce-request-middleware
                         ;; multipart
                         ;multipart/multipart-middleware
                         ]}})

   (r/routes
    (r/create-resource-handler {:path "/"})
    (r/create-default-handler))))


(comment

  (let [enc (m/create (assoc-in m/default-options [:format "application/json" :encoder-opts] {:do-not-fail-on-empty-beans true}))]
    (slurp (m/encode enc "application/json" {:foo "bar"})))

  (->
   ((app nil) {:request-method :post :uri "/api/object" :body "{\"y\": \"2\"}" :headers {"content-type" "application/json"}})
   :body
   slurp)

  (let [app (r/ring-handler (r/router
                             [["/api"
                               ["/object" {:post {:summary "create new auction object"

                                                  ;:parameters {:body [:map [:y string?]]}
                                                  :request {:content {"application/json" {:schema map?}}}
                                                  :responses {200 {:description "response object describing created resource"
                                                                   :content {"application/json" {:schema map?}}}}
                                                  :handler echo-handler}}]]]
                             {:data {:coercion reitit.coercion.malli/coercion
                                     :muuntaja
                                     (m/create (assoc-in m/default-options [:formats "application/json" :encoder-opts] {:do-not-fail-on-empty-beans true}))
                                     :middleware [;; query-params & form-params
                         ;parameters/parameters-middleware
                                                  ;; content-negotiation
                         ;muuntaja/format-negotiate-middleware
                                                  ;; encoding response body
                                                  ;muuntaja/format-response-middleware
                                                  ;; exception handling
                         ;exception/exception-middleware
                                                  ;; decoding request body
                                                  muuntaja/format-request-middleware
                                                  ;; coercing response bodys
                                                  coercion/coerce-response-middleware
                                                  ;; coercing request parameters
                                                  coercion/coerce-request-middleware
                                                  ;; multipart
                         ;multipart/multipart-middleware
                                                  ]}}))]
    (->> {:request-method :post :uri "/api/object" :body "{\"y\": \"2\"}" :headers {"content-type" "application/json"}}
         (app)
         :body))

  )