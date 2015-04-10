(ns worker.models.users
  (:require [clojure.java.jdbc :as sql]
            [korma.db :refer [defdb transaction]]
            [korma.core :refer [defentity select where insert values delete update set-fields]]
            [worker.const :as const]
            [worker.lib.util :as util]
            [worker.models.db :as db])
  (:use [carica.core]))

(defdb korma-db (config :db))

(defn select-user-by-email
  "Eメールからユーザーを検索する"
  [email]
  (first (select :users
    (where {:email email}))))

(defn select-user-by-provider
  "プロバイダユーザーIDからユーザーを検索する"
  [provider provider-user-id]
  (first (select :users
           (where {:provider provider, :provider_user_id provider-user-id,
                   :users.status const/user-normal}))))

(defn select-user-by-id
  "idからユーザーを検索する"
  [id status]
  (first (select :users
           (where {:id id, :status status}))))

(defn select-user-by-hash
  "hashからユーザーを検索する"
  [hash]
  (first (select :users
           (where {:hash hash, :status const/user-normal}))))

(defn insert-user
  "ユーザーを挿入する"
  ([email crypted-password hash nickname status admin]
    (let [now (util/to-sql (util/now))]
      (:id (insert :users
             (values {:email email, :crypted_password crypted-password,
                      :provider nil, :provider_user_id nil, :provider_access_token nil, :provider_access_token_secret nil,
                      :hash hash, :nickname nickname, :status status,
                      :admin admin, :sakura false, :created_at now, :updated_at now})))))
  ([provider provider-user-id provider-access-token provider-access-token-secret
    hash nickname status admin]
    (let [now (util/to-sql (util/now))]
      (:id (insert :users
             (values {:email nil, :crypted_password nil,
                      :provider provider, :provider_user_id provider-user-id,
                      :provider_access_token provider-access-token, :provider_access_token_secret provider-access-token-secret,
                      :hash hash, :nickname nickname, :status status,
                      :admin admin, :sakura false, :created_at now, :updated_at now}))))))

(defn update-user-status
  "ユーザーの状態を更新する"
  [id status]
  (let [now (util/to-sql (util/now))]
    (update :users
      (set-fields {:status status, :updated_at now})
      (where {:id id}))))

(defn update-user-password
  "ユーザーのパスワードを更新する"
  [id crypted_password]
  (let [now (util/to-sql (util/now))]
    (update :users
      (set-fields {:crypted_password crypted_password, :updated_at now})
      (where {:id id, :status const/user-normal}))))

(defn update-user-provider
  "ユーザーのOAuthプロバイダ情報を更新する"
  [provider provider-user-id provider-access-token provider-access-token-secret nickname]
  (let [now (util/to-sql (util/now))]
    (update :users
      (set-fields {:provider_access_token provider-access-token,
                   :provider_access_token_secret provider-access-token-secret,
                   :nickname nickname, :updated_at now})
      (where {:provider provider, :provider_user_id provider-user-id, :status const/user-normal}))))

(defn update-user-email
  "ユーザーのEメールを更新する"
  [user-id email]
  (let [now (util/to-sql (util/now))]
    (update :users
      (set-fields {:email email, :updated_at now})
      (where {:id user-id, :status const/user-normal}))))

(defn update-user-nickname
  "ユーザーのニックネームを更新する"
  [user-id nickname]
  (let [now (util/to-sql (util/now))]
    (update :users
      (set-fields {:nickname nickname, :updated_at now})
      (where {:id user-id, :status const/user-normal}))))

(defn delete-user
  "ユーザーを削除する"
  [id]
  (delete :users
    (where {:id id})))
