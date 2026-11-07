(ns auksjon.db.sqlite
  (:require [auksjon.db :as db]
            [honey.sql :as sql]
            [honey.sql.helpers :as h]
            [jsonista.core :as json]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import (java.time Instant)
           (java.util UUID)))

(def ^:private ddl
  ["CREATE TABLE IF NOT EXISTS item (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      description TEXT NOT NULL DEFAULT '',
      images TEXT NOT NULL DEFAULT '[]',
      main_image TEXT,
      price_low INTEGER,
      price_high INTEGER,
      starting_price INTEGER,
      created_at TEXT NOT NULL)"
   "CREATE TABLE IF NOT EXISTS catalog (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      date TEXT,
      visible INTEGER NOT NULL DEFAULT 0)"
   "CREATE TABLE IF NOT EXISTS catalog_item (
      catalog_id TEXT NOT NULL REFERENCES catalog(id) ON DELETE CASCADE,
      item_id TEXT NOT NULL REFERENCES item(id) ON DELETE CASCADE,
      position INTEGER NOT NULL,
      PRIMARY KEY (catalog_id, item_id))"
   "CREATE INDEX IF NOT EXISTS catalog_item_item ON catalog_item(item_id)"])

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(defn- q [conn sqlmap] (jdbc/execute! conn (sql/format sqlmap) opts))
(defn- q1 [conn sqlmap] (jdbc/execute-one! conn (sql/format sqlmap) opts))
(defn- update-count [result] (:next.jdbc/update-count result 0))

;; --- row <-> entity ---------------------------------------------------------

(defn- parse-uuid* [s] (some-> s UUID/fromString))

(defn- item-catalog-ids [conn item-id]
  (mapv (comp parse-uuid* :catalog_id)
        (q conn (-> (h/select :catalog_id) (h/from :catalog_item)
                    (h/where [:= :item_id (str item-id)])
                    (h/order-by :catalog_id)))))

(defn- row->item [conn row]
  (when row
    {:id (parse-uuid* (:id row))
     :title (:title row)
     :description (:description row)
     :images (json/read-value (:images row))
     :mainImage (:main_image row)
     :priceLow (:price_low row)
     :priceHigh (:price_high row)
     :startingPrice (:starting_price row)
     :createdAt (:created_at row)
     :catalogs (item-catalog-ids conn (:id row))}))

(def ^:private item-cols
  {:title :title, :description :description, :images :images,
   :mainImage :main_image, :priceLow :price_low, :priceHigh :price_high,
   :startingPrice :starting_price})

(defn- item->row [item]
  (reduce-kv (fn [m k col]
               (if (contains? item k)
                 (assoc m col (cond-> (get item k) (= k :images) json/write-value-as-string))
                 m))
             {} item-cols))

(defn- row->catalog [row]
  (when row
    {:id (parse-uuid* (:id row))
     :name (:name row)
     :date (:date row)
     :visible (= 1 (:visible row))}))

(defn- catalog->row [catalog]
  (cond-> (select-keys catalog [:name :date])
    (contains? catalog :visible) (assoc :visible (if (:visible catalog) 1 0))))

;; --- helpers -----------------------------------------------------------------

(defn- get-item* [conn id]
  (row->item conn (q1 conn (-> (h/select :*) (h/from :item) (h/where [:= :id (str id)])))))

(defn- get-catalog* [conn id]
  (row->catalog (q1 conn (-> (h/select :*) (h/from :catalog) (h/where [:= :id (str id)])))))

(defn- catalog-item-ids [conn catalog-id]
  (mapv (comp parse-uuid* :item_id)
        (q conn (-> (h/select :item_id) (h/from :catalog_item)
                    (h/where [:= :catalog_id (str catalog-id)])
                    (h/order-by :position)))))

(defn- write-catalog-items! [conn catalog-id item-ids]
  (q conn (-> (h/delete-from :catalog_item) (h/where [:= :catalog_id (str catalog-id)])))
  (when (seq item-ids)
    (q conn (-> (h/insert-into :catalog_item)
                (h/values (map-indexed (fn [i iid] {:catalog_id (str catalog-id)
                                                    :item_id (str iid)
                                                    :position i})
                                       (distinct item-ids)))))))

;; --- Store -------------------------------------------------------------------

(defrecord SqliteStore [ds]
  db/Store
  (list-catalogs [_ {:keys [visible-only?]}]
    (mapv row->catalog
          (q ds (cond-> (-> (h/select :*) (h/from :catalog) (h/order-by [:date :desc] :name))
                  visible-only? (h/where [:= :visible 1])))))

  (get-catalog [_ id] (get-catalog* ds id))

  (create-catalog [_ catalog]
    (let [id (UUID/randomUUID)]
      (q ds (-> (h/insert-into :catalog)
                (h/values [(assoc (catalog->row (merge {:visible false} catalog)) :id (str id))])))
      (get-catalog* ds id)))

  (update-catalog [_ id patch]
    (jdbc/with-transaction [tx ds]
      (let [row (catalog->row patch)]
        (when (seq row)
          (q tx (-> (h/update :catalog) (h/set row) (h/where [:= :id (str id)]))))
        (get-catalog* tx id))))

  (delete-catalog [_ id]
    (pos? (update-count (q1 ds (-> (h/delete-from :catalog) (h/where [:= :id (str id)]))))))

  (list-items [_]
    (jdbc/with-transaction [tx ds]
      (mapv #(row->item tx %)
            (q tx (-> (h/select :*) (h/from :item) (h/order-by [:created_at :desc]))))))

  (get-item [_ id] (get-item* ds id))

  (create-item [_ item]
    (let [id (UUID/randomUUID)]
      (jdbc/with-transaction [tx ds]
        (q tx (-> (h/insert-into :item)
                  (h/values [(assoc (item->row (merge {:description "" :images []} item))
                                    :id (str id)
                                    :created_at (str (Instant/now)))])))
        (get-item* tx id))))

  (update-item [_ id patch]
    (jdbc/with-transaction [tx ds]
      (let [row (item->row patch)]
        (when (seq row)
          (q tx (-> (h/update :item) (h/set row) (h/where [:= :id (str id)]))))
        (get-item* tx id))))

  (delete-item [_ id]
    (pos? (update-count (q1 ds (-> (h/delete-from :item) (h/where [:= :id (str id)]))))))

  (delete-all-items [_]
    (update-count (q1 ds (h/delete-from :item))))

  (catalog-items [_ catalog-id]
    (jdbc/with-transaction [tx ds]
      (mapv #(row->item tx %)
            (q tx (-> (h/select :i.*) (h/from [:catalog_item :ci])
                      (h/join [:item :i] [:= :i.id :ci.item_id])
                      (h/where [:= :ci.catalog_id (str catalog-id)])
                      (h/order-by :ci.position))))))

  (add-item-to-catalog [_ catalog-id item-id position]
    (jdbc/with-transaction [tx ds]
      (let [ids (vec (remove #{item-id} (catalog-item-ids tx catalog-id)))
            pos (-> (or position (count ids)) (max 0) (min (count ids)))]
        (write-catalog-items! tx catalog-id (concat (subvec ids 0 pos) [item-id] (subvec ids pos))))))

  (remove-item-from-catalog [_ catalog-id item-id]
    (pos? (update-count
           (q1 ds (-> (h/delete-from :catalog_item)
                      (h/where [:= :catalog_id (str catalog-id)] [:= :item_id (str item-id)]))))))

  (set-catalog-items [_ catalog-id item-ids]
    (jdbc/with-transaction [tx ds]
      (write-catalog-items! tx catalog-id item-ids))))

(defn create-store
  "jdbc-url e.g. \"jdbc:sqlite:auksjon.db\". Creates tables if missing."
  [jdbc-url]
  (let [url (str jdbc-url (if (re-find #"\?" jdbc-url) "&" "?") "foreign_keys=on")
        ds (jdbc/get-datasource {:jdbcUrl url})]
    (doseq [stmt ddl] (jdbc/execute! ds [stmt]))
    (->SqliteStore ds)))
