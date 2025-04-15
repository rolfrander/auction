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
            [reitit.coercion.spec]))

(defn app [_config]
  (r/ring-handler
   (r/router
    [["/api"
      ["/ping" {:get (constantly {:status 200, :body "ping"})}]
      ["/pong" {:post (constantly {:status 200, :body "pong"})}]
      ["/object" {:post {:summary "create new auction object"
                         :swagger {:produces ["application/json"]}
                         :parameters {}
                         :responses {200 {:description "response object describing created resource"
                                          :content "application/json"}
                                     401 {:description "user not authenticated"}
                                     403 {:description "does not have permission to create object"}}
                         :handler (constantly {:status 501})}}]
      ["/object/:id" {:get {:summary "retrieve object by id"
                            :swagger {:produces ["application/json"]}
                            :responses {200 {:description "returns auction object"}
                                        401 {:description "user not authenticated"}
                                        403 {:description "does not have permission to retrieve object"}}
                            :handler (constantly {:status 501})}
                      :patch {:summary "update description of object"
                              :swagger {:produces ["application/json"]}
                              :responses {204 {:description "update ok"}
                                          401 {:description "user not authenticated"}
                                          403 {:description "does not have permission to retrieve object"}}
                              :handler (constantly {:status 501})}}]]
     ["" {:no-doc true}
      ["/swagger.json" {:get (swagger/create-swagger-handler)}]
      ["/api-docs/*" {:get (swagger-ui/create-swagger-ui-handler)}]]]
    {:data {;:coercion reitit.coercion.spec/coercion
            :muuntaja m/instance
            :middleware [;; query-params & form-params
                         ;parameters/parameters-middleware
                         ;; content-negotiation
                         ;muuntaja/format-negotiate-middleware
                         ;; encoding response body
                         muuntaja/format-response-middleware
                         ;; exception handling
                         ;exception/exception-middleware
                         ;; decoding request body
                         ;muuntaja/format-request-middleware
                         ;; coercing response bodys
                         ;coercion/coerce-response-middleware
                         ;; coercing request parameters
                         ;coercion/coerce-request-middleware
                         ;; multipart
                         ;multipart/multipart-middleware
                         ]}})

   (r/routes
    (r/create-resource-handler {:path "/"})
    (r/create-default-handler))))