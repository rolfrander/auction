(ns auction.data
  (:require [schema.core :as s]
            [schema.coerce :refer [coercer]]))

(s/defschema Image
  {:id s/Uuid
   (s/optional-key :title) s/Str
   :url s/Str
   })

(s/defschema Item
  {:id s/Uuid
   :title s/Str
   (s/optional-key :description) s/Str
   (s/optional-key :image) Image})


(comment
  (s/validate Item {:title "foo"
                    :id (java.util.UUID/randomUUID)
                    })

  ((coercer Item) {:title "foo"
                   :description ""
                   :id (str (java.util.UUID/randomUUID))
                   :image nil})


  )

