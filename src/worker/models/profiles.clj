(ns worker.models.profiles
  (:require [clojure.java.jdbc :as sql]
            [korma.db :refer [defdb transaction]]
            [korma.core :refer [defentity select where insert values delete update set-fields]]
            [worker.const :as const]
            [worker.lib.util :as util]
            [worker.models.db :as db])
  (:use [carica.core]))

(defdb korma-db (config :db))

(defn select-profile-by-user-id
  "ユーザーIDからプロフィールを検索する"
  [user-id]
  (first (select :profiles
           (where {:user_id user-id}))))

(defn insert-profile
  "プロフィールを挿入する"
  [user-id gender birthday income]
  (let [gender2 (if (or (empty? gender) (> 1(Long/parseLong  gender))) nil (Long/parseLong gender))
        income2 (if (or (empty? income) (> 1 (Long/parseLong income))) nil (Long/parseLong income))
        now (util/to-sql (util/now))
        sql-birthday (if birthday (util/to-sql birthday) nil)]
    (:id
      (insert :profiles
        (values {:user_id user-id, :gender gender2,
                 :birthday sql-birthday, :income income2,
                 :twitter_user_id nil, :twitter_access_token nil, :twitter_access_token_secret nil,
                 :facebook_user_id nil, :facebook_access_token nil, :facebook_access_token_secret nil,
                 :created_at now, :updated_at now})))))

(defn insert-twitter-profile
  "プロフィールを挿入する"
  [user-id twitter-user-id twitter-access-token twitter-access-token-secret]
  (let [now (util/to-sql (util/now))]
    (:id
      (insert :profiles
        (values {:user_id user-id, :gender nil,
                 :birthday nil, :income nil,
                 :twitter_user_id twitter-user-id,
                 :twitter_access_token twitter-access-token, :twitter_access_token_secret twitter-access-token-secret,
                 :facebook_user_id nil, :facebook_access_token nil, :facebook_access_token_secret nil,
                 :created_at now, :updated_at now})))))

(defn update-profile
  "プロフィールを更新する"
  [user-id gender birthday income]
  (let [gender2 (if (or (empty? gender) (> 1(Long/parseLong  gender))) nil (Long/parseLong gender))
        income2 (if (or (empty? income) (> 1 (Long/parseLong income))) nil (Long/parseLong income))
        now (util/to-sql (util/now))
        sql-birthday (if birthday (util/to-sql birthday) nil)]
    (:id
      (update :profiles
        (set-fields {:gender gender2, :birthday sql-birthday, :income income2, :updated_at now})
        (where {:user_id user-id})))))

(defn update-twitter-profile
  "プロフィールを更新する"
  [user-id twitter-user-id twitter-access-token twitter-access-token-secret]
  (let [now (util/to-sql (util/now))]
    (:id
      (update :profiles
        (set-fields {:twitter_user_id twitter-user-id, :twitter_access_token twitter-access-token,
                     :twitter_access_token_secret twitter-access-token-secret, :updated_at now})
        (where {:user_id user-id})))))

(defn delete-profile-by-user-id
  "ユーザーIDからプロフィールを削除する"
  [user-id]
  (delete :profiles
    (where {:user_id user-id})))
