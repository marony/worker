(ns worker.models.access-log
  (:require [clojure.java.jdbc :as sql]
            [korma.db :refer [defdb transaction]]
            [korma.core :refer [defentity insert values]]
            [worker.models.db :as db]
            [worker.lib.util :as util])
  (:use [carica.core]))

(defdb korma-db (config :db))

(comment ; コメント
  "ネストした分配束縛"
  (let [{:keys [a b] {:keys [da db] :as d} :d }
        {:a "A" :b "B" :c "C" :d {:da "DA" :db "DB"}}]
    da))

(defn add-access-log
  "アクセス記録"
  [user-id
   {:keys [remote-addr uri
           query-string request-string
           params server-name
           server-port]
    {:strs [user-agent cokkie referer host accept-language]} :headers
    :as request}
   {:keys [status response-headers response-body]:as response}]
  (transaction
    (:id
      (insert :access_log (values
                            {:access_time (util/to-sql (util/now))
                             :user_id user-id
                             :useragent user-agent
                             :remote_addr remote-addr
                             :uri uri
                             :cokkie cokkie
                             :query_string query-string
                             :request_string request-string
                             :params (str params)
                             :referer referer
                             :accept_language accept-language
                             :server_name server-name
                             :server_port server-port
                             :status status
                             :response_headers response-headers
                             :response_body response-body})))))
