(ns worker.handler
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer [defroutes routes]]
            [noir.util.middleware :as noir-middleware]
            [worker.routes.auth :refer [auth-routes]]
            [worker.routes.home :refer [home-routes]]
            [worker.lib.auth :as lauth]
            [worker.models.access-log :as access]
            [worker.lib.logger :as logger]
            [taoensso.timbre :as tim])
  (:use [ring.adapter.jetty]
        [carica.core]))

(defn init
  "初期化処理"
  []
  (do
    (println "CONFIG1:" (config))
    (logger/init)
    (tim/info "worker is starting")))

(defn destroy
  "後処理"
  []
  (tim/info "worker is shutting down"))

(defroutes app-routes
  "標準のルーティング定義"
  (route/resources "/")
  (route/not-found "Not Found"))

(defn wrap-spy
  "要求・応答ログ出力ハンドラ"
  [handler]
  (fn [request]
    (tim/info (str "REQUEST:" request))
    (let [response (handler request)]
      (tim/info (str "RESPONSE:" (dissoc response :body)))
      (if-let [user (lauth/login?)]
        (access/add-access-log (user :id) request response)
        (access/add-access-log nil request response))
      response)))

;;; アプリケーションのルーティング定義
(def app
  (noir-middleware/app-handler
        [auth-routes home-routes app-routes]
   :middleware [wrap-spy]))

(defn -main []
  (println "CONFIG2:" (config))
  (println "PORT:" (System/getenv "PORT"))
  (let [port (config :port)]
    (run-jetty app {:port port})))

(comment noir-middleware/app-handler
  "creates the handler for the application and wraps it in base middleware:
- wrap-request-map
- api
- wrap-multipart-params
- wrap-noir-validation
- wrap-noir-cookies
- wrap-noir-flash
- wrap-noir-session

:session-options - optional map specifying Ring session parameters, eg: {:cookie-attrs {:max-age 1000}}
:store           - deprecated: use sesion-options instead!
:multipart       - an optional map of multipart-params middleware options
:middleware      - a vector of any custom middleware wrappers you wish to supply
:formats         - optional vector containing formats that should be serialized and
                   deserialized, eg:

                   :formats [:json-kw :edn]

                available formats:
                :json - JSON with string keys in :params and :body-params
                :json-kw - JSON with keywodized keys in :params and :body-params
                :yaml - YAML format
                :yaml-kw - YAML format with keywodized keys in :params and :body-params
                :edn - Clojure format
                :yaml-in-html - yaml in a html page (useful for browser debugging)

:access-rules - a vector of access rules you wish to supply,
                each rule should a function or a rule map as specified in wrap-access-rules, eg:

                :access-rules [rule1
                               rule2
                               {:redirect \"/unauthorized1\"
                                :rules [rule3 rule4]}]")

(comment handler/site
  "Create a handler suitable for a standard website. This adds the
    following middleware to your routes:
      - wrap-session
      - wrap-flash
      - wrap-cookies
      - wrap-multipart-params
      - wrap-params
      - wrap-nested-params
      - wrap-keyword-params

    A map of options may also be provided. These keys are provided:
      :session   - a map of session middleware options
      :multipart - a map of multipart-params middleware options")
