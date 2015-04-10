(ns worker.models.business
  (:require [clojure.java.jdbc :as sql]
            [korma.db :refer [defdb transaction]]
            [korma.core :refer [defentity select where insert values delete update set-fields]]
            [worker.const :as const]
            [worker.lib.util :as util]
            [worker.models.db :as db])
  (:use [carica.core]))

(defdb korma-db (config :db))

(defn select-business-by-user-id
  "ユーザーIDから職業を検索する"
  [user-id]
  (first (select :business
           (where {:user_id user-id}))))

(defn insert-business
  "職業を挿入する"
  [id user-id note]
  (let [now (util/to-sql (util/now))
        id2 (if (or (empty? id) (> 1 (Long/parseLong id))) 0 (Long/parseLong id))]
    (:id
      (insert :business
        (values {:id id2, :user_id user-id, :note note,
                 :created_at now, :updated_at now})))))

(defn update-business
  "職業を更新する"
  [id user-id note]
  (let [now (util/to-sql (util/now))
        id2 (if (or (empty? id) (> 1 (Long/parseLong id))) 0 (Long/parseLong id))]
    (:id
      (update :business
        (set-fields {:id id2, :note note, :updated_at now})
      (where {:user_id user-id})))))

(defn delete-business-by-user-id
  "ユーザーIDからプロフィールを削除する"
  [user-id]
  (delete :business
    (where {:user_id user-id})))
