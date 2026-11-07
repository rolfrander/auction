(ns auksjon.api.session
  "Login/logout. A successful login sets an HttpOnly session cookie that
  authorizes /api/admin/* (see auksjon.auth)."
  (:require [auksjon.auth :as auth]
            [auksjon.schema :as s]))

(def Session [:map [:username :string]])

(defn login [{:keys [auth parameters] :as request}]
  (let [{:keys [username password]} (:body parameters)]
    (if (auth/check-credentials auth username password)
      {:status 200
       :headers {"Set-Cookie" (auth/login-cookie auth request)}
       :body {:username (:user auth)}}
      {:status 401 :body {:error "invalid username or password"}})))

(defn logout [request]
  {:status 204 :headers {"Set-Cookie" (auth/logout-cookie request)}})

(defn session [{:keys [auth]}]
  {:status 200 :body {:username (:user auth)}})

(def public-routes
  [["/login" {:swagger {:tags ["session"]}
              :post {:summary "Log in; sets the session cookie used by /api/admin/*"
                     :parameters {:body [:map {:closed true}
                                         [:username :string]
                                         [:password :string]]}
                     :responses {200 {:body Session}
                                 401 {:body s/ErrorBody}}
                     :handler login}}]
   ["/logout" {:swagger {:tags ["session"]}
               :post {:summary "Log out; clears the session cookie"
                      :responses {204 {:description "Logged out"}}
                      :handler logout}}]])

(def admin-routes
  ["/session" {:swagger {:tags ["session"]}
               :get {:summary "Current session (401 if not logged in)"
                     :responses {200 {:body Session}}
                     :handler session}}])
