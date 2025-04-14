(ns auction.portfolio
  (:require [portfolio.ui :as ui]
            [auction.scenes]))


;<link rel= "stylesheet" href= "https://cdn.jsdelivr.net/npm/bulma@0.9.4/css/bulma.min.css" >
;<script defer src= "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.2.0/css/all.min.css" ></script>

(ui/start!
 {:config
  {:css-paths ["https://cdn.jsdelivr.net/npm/bulma@0.9.4/css/bulma.min.css"]}})
