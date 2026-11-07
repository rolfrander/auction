(ns auksjon.db
  "Storage abstraction. Handlers depend only on this protocol; implementations
  live in auksjon.db.* (currently SQLite).

  Entities are plain maps with the same keys as the API JSON:
    item    {:id :title :description :images :mainImage :priceLow :priceHigh
             :startingPrice :createdAt :catalogs}
    catalog {:id :name :date :visible}
  Ids are java.util.UUID. Lookups return nil when not found.")

(defprotocol Store
  (list-catalogs [store opts] "opts: {:visible-only? bool}")
  (get-catalog [store id])
  (create-catalog [store catalog] "Returns the created catalog.")
  (update-catalog [store id patch] "Partial update. Returns updated catalog or nil.")
  (delete-catalog [store id] "Returns true if deleted.")

  (list-items [store])
  (get-item [store id])
  (create-item [store item] "Returns the created item.")
  (update-item [store id patch] "Partial update. Returns updated item or nil.")
  (delete-item [store id] "Returns true if deleted.")
  (delete-all-items [store] "Returns number of deleted items.")

  (catalog-items [store catalog-id] "Items in catalog order.")
  (add-item-to-catalog [store catalog-id item-id position]
    "Inserts item at position (0-based; nil = append). Moves it if already present.")
  (remove-item-from-catalog [store catalog-id item-id] "Returns true if removed.")
  (set-catalog-items [store catalog-id item-ids] "Replaces catalog contents with item-ids in that order."))
