(ns worker.models.actions
  (:require [clojure.java.jdbc :as sql]
            [korma.db :refer [defdb transaction]]
            [korma.core :refer [defentity select where insert values delete]]
            [worker.const :as const]
            [worker.lib.util :as util]
            [worker.models.db :as db])
  (:use [carica.core]))

(defdb korma-db (config :db))

(defn select-action-by-token
  "tokenからアクションを検索する"
  [token type status]
  (let [now (util/to-sql (util/now))]
    (first (select :actions
             (where {:token token, :type type, :status status, :limit_time [>= now]})))))

(defn insert-action
  "アクションを挿入する"
  [token limit-time type status user-id note]
  (let [now (util/to-sql (util/now))]
    (:id
      (insert :actions
        (values {:token token, :limit_time (util/to-sql limit-time),
                 :type type, :status status, :user_id user-id, :note note,
                 :created_at now, :updated_at now})))))

(defn delete-action-by-user-id
  "ユーザーIDからアクションを削除する"
  [user-id]
  (delete :actions
    (where {:user_id user-id})))

(defn delete-action-by-token
  "トークンからアクションを削除する"
  [token]
  (delete :actions
    (where {:token token})))
