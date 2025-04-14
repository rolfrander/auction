(ns auction.system 
  (:require
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [reitit.ring :as r]
   [ring.adapter.jetty :refer [run-jetty]]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.multipart :as multipart]
   [reitit.ring.middleware.parameters :as parameters]
   [muuntaja.core :as m]
   [reitit.coercion.spec]))
   

(def system
  {:app/config {}
   :app/handler {}
   :app/adapter {:config (ig/ref :app/config)
                 :handler (ig/ref :app/handler)}})
   

(defmethod ig/init-key :app/config [_ _]
  (read-string (slurp (io/resource "config.edn"))))

(defmethod ig/init-key :app/handler [_ _]
  (r/ring-handler
   (r/router
    [["/api"
      ["/ping" {:get (constantly {:status 200, :body "ping"})}]
      ["/pong" {:post (constantly {:status 200, :body "pong"})}]]
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

(defmethod ig/init-key :app/adapter [_ {:keys [config handler]}]
  (run-jetty handler (assoc config :join? false)))
  

(defmethod ig/halt-key! :app/adapter [_ jetty]
  (.stop jetty))

(comment)
  
  