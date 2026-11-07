(ns auksjon.handler
  (:require [auksjon.api.catalogs :as catalogs]
            [auksjon.auth :as auth]
            [auksjon.api.images :as images]
            [auksjon.api.items :as items]
            [auksjon.api.session :as session]
            [muuntaja.core :as m]
            [reitit.coercion :as coercion]
            [reitit.coercion.malli :as malli-coercion]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as ring-coercion]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [ring.util.response :as resp]))

(defn health [_request]
  {:status 200
   :body {:status "ok"}})

(defn routes []
  [["/api"
    ["/swagger.json" {:get {:no-doc true
                            :swagger {:info {:title "Auksjon API" :version "0.1.0"
                                             :description "/api/admin/* requires the session cookie set by POST /api/login."}}
                            :handler (swagger/create-swagger-handler)}}]
    ["/health" {:get {:summary "Health check" :handler health}}]
    catalogs/public-routes
    session/public-routes
    ;; Session cookie is enforced by auth/wrap-admin-auth in `app`.
    ["/admin"
     session/admin-routes
     catalogs/admin-routes
     items/admin-routes
     images/admin-routes]]])

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

(defn wrap-deps [handler deps]
  (fn [req] (handler (merge req deps))))

(defn admin-spa-handler
  "Serves admin/index.html for client-side routes under /admin (paths without a file extension)."
  [static-dir]
  (fn [{:keys [request-method uri]}]
    (when (and (= :get request-method) (re-matches #"/admin(/[^.]*)?" uri))
      (some-> (resp/file-response "admin/index.html" {:root static-dir})
              (resp/content-type "text/html; charset=utf-8")))))

(defn app
  "Ring handler. Serves /api/* as JSON, Swagger UI at /api/docs and, if
  `static-dir` is given, the built frontend for everything else.
  `store` is an auksjon.db/Store, `image-store` an auksjon.images/ImageStore,
  `image-base-url` the public URL prefix images are served from,
  `auth` {:user :password-hash} guarding /api/admin/* (see auksjon.auth)."
  [{:keys [store image-store image-base-url static-dir auth]}]
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
                         ring-coercion/coerce-response-middleware
                         multipart/multipart-middleware]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler {:path "/api/docs" :url "/api/swagger.json"})
    (if static-dir
      (ring/routes
       (admin-spa-handler static-dir)
       (ring/create-file-handler {:path "/" :root static-dir}))
      (constantly nil))
    (ring/create-default-handler))
   {:middleware [[auth/wrap-admin-auth auth]
                 [wrap-deps {:store store :image-store image-store :image-base-url image-base-url :auth auth}]]}))
