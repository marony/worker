(ns worker.lib.twitter
  (:require [noir.session :as session]
            [worker.lib.auth :as lauth]
            [worker.models.users :as users]
            [worker.models.profiles :as profiles]
            [taoensso.timbre :as tim])
  (:use [carica.core])
  (:import (twitter4j conf.ConfigurationBuilder TwitterFactory Twitter auth.RequestToken auth.AccessToken)))

(def consumer-key (config :twitter-api-key))
(def consumer-secret (config :twitter-api-secret))

(defn make-twitter
  []
  (let [factory (TwitterFactory.)
        twitter (.getInstance factory)]
    (doto twitter
      (.setOAuthConsumer consumer-key consumer-secret))))

(defn put-request-token-to-session
  "リクエストトークンをセッションに入れる"
  [request-token]
  (session/put! :token (.getToken request-token))
  (session/put! :token-secret (.getTokenSecret request-token)))

(defn get-request-token-from-session
  "セッションからリクエストトークンを作成する"
  []
  (RequestToken. (session/get! :token) (session/get! :token-secret)))

(defn make-request-token
  "リクエストトークンを作成する"
  ([twitter callback]
    (.getOAuthRequestToken twitter
      (str (config :site-url) callback))))

(defn make-access-token-from-profile
  "プロフィールからアクセストークンを作成する"
  [profile]
  (if (:twitter_user_id profile)
    (AccessToken.
      (:twitter_access_token profile)
      (:twitter_access_token_secret profile)
      (:twitter_user_id profile))
    nil))

(defn make-twitter-data-from-verifier
  "Twitter OAuthコールバック用関数"
  [oauth_token oauth_verifier]
  (if oauth_token
    ; ユーザがOKした
    (do
      (tim/info (str "TWITTER_CALLBACK oauth_token: " oauth_token ", oauth_verifier: " oauth_verifier))
      (let [twitter (make-twitter)
            request-token (get-request-token-from-session)
            access-token (.getOAuthAccessToken twitter request-token oauth_verifier)]
        (tim/info (str "TWITTER_ACCESS user-id: " (.getUserId access-token) ", access-token: " (.getToken access-token) ", access-token-secret: " (.getTokenSecret access-token) ", access-token-user-id: " (.getUserId access-token)))
        (.setOAuthAccessToken twitter access-token)
        (let [twitter-user (.verifyCredentials twitter)
              twitter-user-id (.getId twitter-user)
              twitter-name (.getName twitter-user)
              twitter-screen-name (.getScreenName twitter-user)
              twitter-access-token (.getToken access-token)
              twitter-access-token-secret (.getTokenSecret access-token)]
          (tim/info (str "TWITTER_LOGIN " twitter-user-id ", " twitter-name ", " twitter-screen-name))
          (let [twitter-data {:twitter twitter, :request-token request-token, :access-token access-token,
                              :twitter-user twitter-user, :twitter-user-id twitter-user-id, :twitter-name twitter-name,
                              :twitter-screen-name twitter-screen-name, :twitter-access-token twitter-access-token,
                              :twitter-access-token-secret twitter-access-token-secret}]
            (tim/info (str "TWITTER_DATA " twitter-data))
            twitter-data))))
    ; ユーザが拒否した
    nil))

(defn internal-twitter-login
  "Twitterにログインする"
  [callback]
  (let [twitter (make-twitter)
        request-token (make-request-token twitter callback)]
    (if request-token
      (tim/info (str "TWITTER_AUTH request-token: " (.getToken request-token) ", request-token-secret: " (.getTokenSecret request-token)))
      (tim/info "TWITTER_AUTH request-token: nil"))
    (put-request-token-to-session request-token)
    request-token))

(defn twitter-login
  "Twitterでログインする"
  [callback]
  (try
    (if-let [user (lauth/login?)]
      ; ログイン済
      nil
      ; Twitterでログインする
      (internal-twitter-login callback))
    (catch Exception ex
      (tim/info (.toString ex))
      ; 例外
      nil)))

(defn handle-registration-callback
  "ユーザー登録コールバック処理関数"
  [twitter-data]
  ; ユーザがOKした
  (let [twitter-user-id (:twitter-user-id twitter-data)
        twitter-name (:twitter-name twitter-data)
        twitter-access-token (:twitter-access-token twitter-data)
        twitter-access-token-secret (:twitter-access-token-secret twitter-data)]
    ; prover-user-idからユーザ検索
    (if-let [user (users/select-user-by-provider "twitter" twitter-user-id)]
      ; 登録済(ログイン)
      (do
        (users/update-user-provider "twitter" twitter-user-id twitter-access-token twitter-access-token-secret twitter-name)
        (lauth/login user)
        nil)
      ; 未登録(ユーザー登録へ)
      {:provider "twitter", :twitter-user (:twitter-user twitter-data), :provider-user-id twitter-user-id,
       :nickname twitter-name, :twitter-screen-name (:twitter-screen-name twitter-data),
       :provider-access-token twitter-access-token,
       :provider-access-token-secret twitter-access-token-secret})))

(defn handle-tweet-callback
  "つぶやきコールバック処理関数"
  [twitter-data]
  ; ユーザがOKした
  (if-let [user (lauth/login?)]
    ; ログイン済
    (let [user-id (:id user)
          twitter (:twitter twitter-data)
          twitter-user-id (:twitter-user-id twitter-data)
          twitter-access-token (:twitter-access-token twitter-data)
          twitter-access-token-secret (:twitter-access-token-secret twitter-data)
          tweet (session/get! :tweet)]
      (profiles/update-twitter-profile
        user-id twitter-user-id twitter-access-token twitter-access-token-secret)
      (if (> (config :tweet) 0)
        (.updateStatus twitter tweet))
      (println "つぶやきました = " tweet)
      true)
    ; 未ログイン
    nil))

(defn twitter-callback
  [oauth_token oauth_verifier f]
  (try
    (if-let [twitter-data (make-twitter-data-from-verifier oauth_token oauth_verifier)]
      (f twitter-data)
      ; ユーザが拒否した
      nil)
    (catch Exception ex
      (tim/info (.toString ex))
      ; 例外
      nil)))

(defn tweet
  [user tweet callback]
  (try
    (let [twitter (make-twitter)
          user-id (:id user)
          profile (profiles/select-profile-by-user-id user-id)
          access-token (make-access-token-from-profile profile)
          result (if access-token
                   (do
                     (doto twitter
                       (.setOAuthAccessToken access-token)
                       (.verifyCredentials)
                       (if (> (config :tweet) 0)
                         (.updateStatus tweet)))
                     (println "つぶやきました = " tweet))
                   nil)]
      (if result
        ; つぶやき完了(Twitterログイン済)
        nil
        ; Twitterログイン処理
        (if-let [request-token (internal-twitter-login callback)]
          ; ログイン処理続行
          (do
            (session/put! :tweet tweet)
            request-token)
          ; 不明
          nil)))
    (catch Exception ex
      ; TODO: "Twitterにつぶやきました"が表示されないように
      (tim/info (.toString ex))
      ; 例外
      nil)))
