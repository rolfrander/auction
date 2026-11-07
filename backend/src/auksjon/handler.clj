(ns auksjon.handler
  (:require [auksjon.api.catalogs :as catalogs]
            [auksjon.api.items :as items]
            [muuntaja.core :as m]
            [reitit.coercion :as coercion]
            [reitit.coercion.malli :as malli-coercion]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as ring-coercion]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]))

(defn health [_request]
  {:status 200
   :body {:status "ok"}})

(defn routes []
  [["/api"
    ["/swagger.json" {:get {:no-doc true
                            :swagger {:info {:title "Auksjon API" :version "0.1.0"}}
                            :handler (swagger/create-swagger-handler)}}]
    ["/health" {:get {:summary "Health check" :handler health}}]
    catalogs/public-routes
    ;; Admin routes: basic-auth middleware goes here later.
    ["/admin"
     catalogs/admin-routes
     items/admin-routes]]])

(defn- coercion-error [status]
  (fn [e _req]
    {:status status
     :body {:error (if (= status 400) "invalid request" "invalid response")
            :details (coercion/encode-error (ex-data e))}}))

(def exception-middleware
  (exception/create-exception-middleware
   (merge exception/default-handlers
          {::coercion/request-coercion (coercion-error 400)
           ::coercion/response-coercion (coercion-error 500)})))

(defn wrap-store [handler store]
  (fn [req] (handler (assoc req :store store))))

(defn app
  "Ring handler. Serves /api/* as JSON, Swagger UI at /api/docs and, if
  `static-dir` is given, the built frontend for everything else."
  [{:keys [store static-dir]}]
  (ring/ring-handler
   (ring/router
    (routes)
    {:data {:muuntaja m/instance
            ;; reject unknown keys (closed maps) instead of silently stripping them
            :coercion (malli-coercion/create {:strip-extra-keys false})
            :middleware [swagger/swagger-feature
                         parameters/parameters-middleware
                         muuntaja/format-middleware
                         exception-middleware
                         ring-coercion/coerce-request-middleware
                         ring-coercion/coerce-response-middleware]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler {:path "/api/docs" :url "/api/swagger.json"})
    (if static-dir
      (ring/create-file-handler {:path "/" :root static-dir})
      (constantly nil))
    (ring/create-default-handler))
   {:middleware [[wrap-store store]]}))
