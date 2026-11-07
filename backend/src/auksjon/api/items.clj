(ns auksjon.api.items
  (:require [auksjon.db :as db]
            [auksjon.schema :as s]))

(defn- not-found [] {:status 404 :body {:error "item not found"}})

(defn list-items [{:keys [store]}]
  {:status 200 :body (db/list-items store)})

(defn create-item [{:keys [store parameters]}]
  (let [item (db/create-item store (:body parameters))]
    {:status 201
     :headers {"Location" (str "/api/admin/items/" (:id item))}
     :body item}))

(defn delete-all-items [{:keys [store]}]
  {:status 200 :body {:deleted (db/delete-all-items store)}})

(defn get-item [{:keys [store parameters]}]
  (if-let [item (db/get-item store (-> parameters :path :id))]
    {:status 200 :body item}
    (not-found)))

(defn update-item [{:keys [store parameters]}]
  (if-let [item (db/update-item store (-> parameters :path :id) (:body parameters))]
    {:status 200 :body item}
    (not-found)))

(defn delete-item [{:keys [store parameters]}]
  (if (db/delete-item store (-> parameters :path :id))
    {:status 204}
    (not-found)))

(def admin-routes
  ["/items"
   {:swagger {:tags ["admin: items"]}}
   ["" {:get {:summary "List all items"
              :responses {200 {:body [:vector s/Item]}}
              :handler list-items}
        :post {:summary "Create item"
               :parameters {:body s/ItemInput}
               :responses {201 {:body s/Item}}
               :handler create-item}
        :delete {:summary "Delete ALL items"
                 :responses {200 {:body s/Deleted}}
                 :handler delete-all-items}}]
   ["/:id" {:parameters {:path [:map [:id :uuid]]}
            :get {:summary "Get item"
                  :responses {200 {:body s/Item} 404 {:body s/ErrorBody}}
                  :handler get-item}
            :patch {:summary "Update item (partial)"
                    :parameters {:body s/ItemPatch}
                    :responses {200 {:body s/Item} 404 {:body s/ErrorBody}}
                    :handler update-item}
            :delete {:summary "Delete item"
                     :responses {204 {} 404 {:body s/ErrorBody}}
                     :handler delete-item}}]])
