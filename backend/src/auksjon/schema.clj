(ns auksjon.schema
  "Malli schemas for the API (used for coercion and the OpenAPI/Swagger spec).")

(def Price [:maybe nat-int?])
(def Date [:re {:json-schema/example "2026-12-01"} #"^\d{4}-\d{2}-\d{2}$"])

(def ItemFields
  [[:title [:string {:min 1}]]
   [:description {:optional true} :string]
   [:images {:optional true :description "Image URLs/keys; the first is the main image"} [:vector :string]]
   [:priceLow {:optional true} Price]
   [:priceHigh {:optional true} Price]
   [:startingPrice {:optional true} Price]])

(def ItemInput (into [:map {:closed true}] ItemFields))

(def ItemPatch
  (into [:map {:closed true}]
        (map (fn [[k & more]]
               (let [[props s] (if (map? (first more)) more [nil (first more)])]
                 [k (assoc props :optional true) s])))
        ItemFields))

(def Item
  [:map
   [:id :uuid]
   [:title :string]
   [:description :string]
   [:images {:description "The first is the main image"} [:vector :string]]
   [:priceLow Price]
   [:priceHigh Price]
   [:startingPrice Price]
   [:createdAt :string]
   [:catalogs [:vector :uuid]]])

(def CatalogInput
  [:map {:closed true}
   [:name [:string {:min 1}]]
   [:date {:optional true} [:maybe Date]]
   [:visible {:optional true} :boolean]])

(def CatalogPatch
  [:map {:closed true}
   [:name {:optional true} [:string {:min 1}]]
   [:date {:optional true} [:maybe Date]]
   [:visible {:optional true} :boolean]])

(def Catalog
  [:map
   [:id :uuid]
   [:name :string]
   [:date [:maybe :string]]
   [:visible :boolean]])

(def ItemIds [:vector :uuid])

(def ErrorBody [:map [:error :string]])

(def Deleted [:map [:deleted nat-int?]])
