(ns ^:figwheel-hooks auction.dev
  (:require [auction.client.main :as main]))

(defn ^:after-load render []
  (main/render))

