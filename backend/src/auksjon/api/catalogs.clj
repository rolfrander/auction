(ns auksjon.api.catalogs
  (:require [auksjon.db :as db]
            [auksjon.schema :as s]))

(defn- not-found [what] {:status 404 :body {:error (str what " not found")}})

(defn- catalog-id [req] (-> req :parameters :path :id))

;; --- public ------------------------------------------------------------------

(defn list-visible-catalogs [{:keys [store]}]
  {:status 200 :body (db/list-catalogs store {:visible-only? true})})

(defn list-visible-catalog-items [{:keys [store] :as req}]
  (let [catalog (db/get-catalog store (catalog-id req))]
    (if (:visible catalog)
      {:status 200 :body (db/catalog-items store (:id catalog))}
      (not-found "catalog"))))

;; --- admin -------------------------------------------------------------------

(defn list-catalogs [{:keys [store]}]
  {:status 200 :body (db/list-catalogs store {})})

(defn create-catalog [{:keys [store parameters]}]
  (let [catalog (db/create-catalog store (:body parameters))]
    {:status 201
     :headers {"Location" (str "/api/admin/catalogs/" (:id catalog))}
     :body catalog}))

(defn get-catalog [{:keys [store] :as req}]
  (if-let [catalog (db/get-catalog store (catalog-id req))]
    {:status 200 :body catalog}
    (not-found "catalog")))

(defn update-catalog [{:keys [store parameters] :as req}]
  (if-let [catalog (db/update-catalog store (catalog-id req) (:body parameters))]
    {:status 200 :body catalog}
    (not-found "catalog")))

(defn delete-catalog [{:keys [store] :as req}]
  (if (db/delete-catalog store (catalog-id req))
    {:status 204}
    (not-found "catalog")))

(defn- with-catalog [{:keys [store] :as req} f]
  (if (db/get-catalog store (catalog-id req))
    (f)
    (not-found "catalog")))

(defn catalog-items [{:keys [store] :as req}]
  (with-catalog req #(hash-map :status 200 :body (db/catalog-items store (catalog-id req)))))

(defn set-catalog-items [{:keys [store parameters] :as req}]
  (with-catalog req
    #(let [ids (:body parameters)
           missing (remove (fn [id] (db/get-item store id)) ids)]
       (if (seq missing)
         {:status 400 :body {:error (str "unknown item ids: " (vec missing))}}
         (do (db/set-catalog-items store (catalog-id req) ids)
             {:status 200 :body (db/catalog-items store (catalog-id req))})))))

(defn add-item [{:keys [store parameters] :as req}]
  (with-catalog req
    #(let [item-id (-> parameters :path :item-id)]
       (if (db/get-item store item-id)
         (do (db/add-item-to-catalog store (catalog-id req) item-id (-> parameters :query :position))
             {:status 200 :body (db/catalog-items store (catalog-id req))})
         (not-found "item")))))

(defn remove-item [{:keys [store parameters] :as req}]
  (with-catalog req
    #(if (db/remove-item-from-catalog store (catalog-id req) (-> parameters :path :item-id))
       {:status 204}
       (not-found "item in catalog"))))

;; --- routes ------------------------------------------------------------------

(def ^:private id-path {:path [:map [:id :uuid]]})

(def public-routes
  ["/catalogs"
   {:swagger {:tags ["public"]}}
   ["" {:get {:summary "List visible catalogs"
              :responses {200 {:body [:vector s/Catalog]}}
              :handler list-visible-catalogs}}]
   ["/:id/items" {:get {:summary "List items in a visible catalog, in catalog order"
                        :parameters id-path
                        :responses {200 {:body [:vector s/Item]} 404 {:body s/ErrorBody}}
                        :handler list-visible-catalog-items}}]])

(def admin-routes
  ["/catalogs"
   {:swagger {:tags ["admin: catalogs"]}}
   ["" {:get {:summary "List all catalogs"
              :responses {200 {:body [:vector s/Catalog]}}
              :handler list-catalogs}
        :post {:summary "Create catalog"
               :parameters {:body s/CatalogInput}
               :responses {201 {:body s/Catalog}}
               :handler create-catalog}}]
   ["/:id" {:parameters id-path}
    ["" {:get {:summary "Get catalog"
               :responses {200 {:body s/Catalog} 404 {:body s/ErrorBody}}
               :handler get-catalog}
         :patch {:summary "Update catalog (partial)"
                 :parameters {:body s/CatalogPatch}
                 :responses {200 {:body s/Catalog} 404 {:body s/ErrorBody}}
                 :handler update-catalog}
         :delete {:summary "Delete catalog (items are kept)"
                  :responses {204 {} 404 {:body s/ErrorBody}}
                  :handler delete-catalog}}]
    ["/items"
     ["" {:get {:summary "List items in catalog, in catalog order"
                :responses {200 {:body [:vector s/Item]} 404 {:body s/ErrorBody}}
                :handler catalog-items}
          :put {:summary "Replace catalog contents / reorder (ordered list of item ids)"
                :parameters {:body s/ItemIds}
                :responses {200 {:body [:vector s/Item]} 400 {:body s/ErrorBody} 404 {:body s/ErrorBody}}
                :handler set-catalog-items}}]
     ["/:item-id" {:parameters {:path [:map [:item-id :uuid]]}
                   :put {:summary "Add item to catalog (or move it). position: 0-based, default last"
                         :parameters {:query [:map [:position {:optional true} nat-int?]]}
                         :responses {200 {:body [:vector s/Item]} 404 {:body s/ErrorBody}}
                         :handler add-item}
                   :delete {:summary "Remove item from catalog"
                            :responses {204 {} 404 {:body s/ErrorBody}}
                            :handler remove-item}}]]]])
