(ns worker.lib.auth
  (:require [worker.lib.util :as util]
            [worker.const :as const]
            [worker.models.users :as users]
            [clojure.string :as str]
            [noir.session :as session]
            [noir.util.crypt :as crypt]))

(defn login
  "ログイン処理"
  [user]
  (let [user-id (:id user)]
    (session/put! const/session-login user-id)))

(defn logoff
  "ログオフ処理"
  []
  (session/remove! const/session-login))

(defn login?
  "ログインされているか"
  []
  (let [user-id (session/get const/session-login)]
    (if user-id
      (let [user (users/select-user-by-id user-id const/user-normal)]
        (if (empty? user)
          nil
          user))
      nil)))

(defn compare-password
  "パスワードチェック"
  [password crypted-password]
  (crypt/compare password crypted-password))

(defn create-random-token
  "ランダムなトークンを作成する"
  []
  (str/replace (str (java.util.UUID/randomUUID)) #"-" ""))

(defn encrypt-password
  "パスワードを暗号化する"
  [password]
  (crypt/encrypt password))
