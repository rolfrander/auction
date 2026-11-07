(ns auksjon.db.sqlite-test
  (:require [auksjon.db :as db]
            [auksjon.test-util :refer [fresh-store]]
            [clojure.test :refer [deftest is testing]]))

(deftest item-crud
  (let [s (fresh-store)
        item (db/create-item s {:title "Vase" :priceLow 100 :priceHigh 200 :images ["a.jpg" "b.jpg"]})]
    (is (uuid? (:id item)))
    (is (= {:title "Vase" :description "" :images ["a.jpg" "b.jpg"] :mainImage nil
            :priceLow 100 :priceHigh 200 :startingPrice nil :catalogs []}
           (dissoc item :id :createdAt)))
    (is (= item (db/get-item s (:id item))))
    (is (= "Stor vase" (:title (db/update-item s (:id item) {:title "Stor vase"}))))
    (is (= 200 (:priceHigh (db/get-item s (:id item)))) "patch keeps other fields")
    (is (nil? (db/update-item s (random-uuid) {:title "x"})))
    (is (true? (db/delete-item s (:id item))))
    (is (false? (db/delete-item s (:id item))))
    (is (nil? (db/get-item s (:id item))))))

(deftest catalog-membership
  (let [s (fresh-store)
        c (db/create-catalog s {:name "Høst" :date "2026-11-01" :visible true})
        [a b c3] (map #(:id (db/create-item s {:title %})) ["a" "b" "c"])
        titles #(mapv :title (db/catalog-items s (:id c)))]
    (db/add-item-to-catalog s (:id c) a nil)
    (db/add-item-to-catalog s (:id c) b nil)
    (db/add-item-to-catalog s (:id c) c3 0)
    (is (= ["c" "a" "b"] (titles)))
    (testing "re-adding moves"
      (db/add-item-to-catalog s (:id c) c3 nil)
      (is (= ["a" "b" "c"] (titles))))
    (is (= [(:id c)] (:catalogs (db/get-item s a))))
    (db/set-catalog-items s (:id c) [b a])
    (is (= ["b" "a"] (titles)))
    (is (true? (db/remove-item-from-catalog s (:id c) b)))
    (is (= ["a"] (titles)))
    (testing "deleting item cascades"
      (db/delete-item s a)
      (is (= [] (titles))))
    (testing "deleting catalog keeps items"
      (db/add-item-to-catalog s (:id c) c3 nil)
      (db/delete-catalog s (:id c))
      (is (= [] (:catalogs (db/get-item s c3)))))))

(deftest catalog-visibility
  (let [s (fresh-store)]
    (db/create-catalog s {:name "Synlig" :visible true})
    (db/create-catalog s {:name "Skjult"})
    (is (= ["Synlig"] (map :name (db/list-catalogs s {:visible-only? true}))))
    (is (= 2 (count (db/list-catalogs s {}))))))

(deftest delete-all
  (let [s (fresh-store)]
    (dotimes [i 3] (db/create-item s {:title (str i)}))
    (is (= 3 (db/delete-all-items s)))
    (is (= [] (db/list-items s)))))
