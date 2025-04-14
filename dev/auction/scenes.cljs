(ns auction.scenes
  (:require [portfolio.reagent-18 :refer-macros [defscene]]
            [auction.client.components :as c]))

(defscene empty-input
  (let [state (atom "")]
    (c/input "navn" state))
  )

(defscene input-name
  (let [state (atom "foo bar")]
    (c/input "navn" state)))
