(ns auction.client.components)

(defn input [prompt value]
  [:p prompt [:input {:type "text"
                      :value @value
                      :on-change #(reset! ~value (-> % .-target .-value))}]])

