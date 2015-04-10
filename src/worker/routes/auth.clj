(ns worker.routes.auth
  (:require [compojure.core :refer [defroutes GET POST]]
            [worker.const :as const]
            [worker.views.layout :as layout]
            [worker.views.auth :as auth-view]
            [worker.views.home :as home-view]
            [worker.lib.email :as email]
            [worker.lib.util :as util]
            [worker.lib.auth :as lauth]
            [worker.lib.twitter :as twitter]
            [worker.models.users :as users]
            [worker.models.actions :as actions]
            [worker.models.profiles :as profiles]
            [worker.models.business :as business]
            [clj-time.core :as tcore]
            [korma.db :refer [transaction]]
            [noir.response :as res]
            [noir.validation :as vali]
            [noir.session :as session]
            [taoensso.timbre :as tim]))

(defn- valid-nickname? [nickname]
  (vali/rule (vali/has-value? nickname)
    [:nickname "ニックネームを入力してください。"])
  (vali/rule (and (vali/min-length? nickname 1) (vali/max-length? nickname 255))
    [:nickname "ニックネームは255文字以下で入力してください"]))

(defn- valid-email? [email]
  (vali/rule (vali/has-value? email)
    [:email "メールアドレスを入力してください。"])
  (vali/rule (and (vali/min-length? email 3) (vali/max-length? email 255))
    [:email "メールアドレスは3文字以上255文字以下で入力してください"])
  (vali/rule (vali/is-email? email)
    [:email "メールアドレスの形式が間違っています"])
  ; usersテーブルに既にEメールが登録されているかチェック
  ; ステータスが「登録中」の場合は、関連テーブルを削除して再登録
  (let [user (users/select-user-by-email email)
        is-empty (empty? user)]
    (if-not is-empty
      (if (= (user :status) const/user-registing)
        (transaction
          (users/delete-user (user :id))
          (actions/delete-action-by-user-id (user :id))
          (profiles/delete-profile-by-user-id (user :id))
          (business/delete-business-by-user-id (user :id)))
        (vali/rule false
          [:email "メールアドレスは既に登録されています"])))))

(defn- valid-email2? [email]
  (if (not (empty? email))
    (valid-email? email)))

(defn- valid-password? [password password-confirm]
  (vali/rule (vali/has-value? password)
    [:password "パスワードを入力してください。"])
  (vali/rule (and (vali/min-length? password 5) (vali/max-length? password 255))
    [:password "パスワードは5文字以上255文字以下で入力してください"])
  (vali/rule (vali/has-value? password-confirm)
    [:password-confirm "パスワード(確認)を入力してください。"])
  (vali/rule (= password password-confirm)
    [:password-confirm "「パスワード」と「パスワード(確認)」が一致しません)"]))

(defn- valid-gender? [gender]
  (if (vali/has-value? gender)
    (do
      (vali/rule (vali/valid-number? gender)
        [:gender "*"])
      (if (not (vali/errors? :gender))
        (vali/rule (const/master-contains? (Long/parseLong gender) const/gender-list)
          [:gender "*"])))))

(defn- valid-birthday? [birthday-year birthday-month birthday-day]
  (vali/rule (or (and (< 0 (Long/parseLong birthday-year)) (< 0 (Long/parseLong birthday-month)) (< 0 (Long/parseLong birthday-day)))
               (and (= 0 (Long/parseLong birthday-year)) (= 0 (Long/parseLong birthday-month)) (= 0 (Long/parseLong birthday-day))))
    [:birthday "「年」「月」「日」すべてを選んでください。入力しない場合は、すべて「なし」を選択してください"])
  (if (and (< 0 (Long/parseLong birthday-year)) (< 0 (Long/parseLong birthday-month)) (< 0 (Long/parseLong birthday-day)))
    (do
      (vali/rule (and (>= (Long/parseLong birthday-year) const/min-year) (<= (Long/parseLong birthday-year) (const/max-year)))
        [:birthday-year (str const/min-year "年から" (const/max-year) "年を選んでください")])
      (vali/rule (and (>= (Long/parseLong birthday-month) const/min-month) (<= (Long/parseLong birthday-month) const/max-month))
        [:birthday-month (str const/min-month "月から" const/max-month "月を選んでください")])
      (vali/rule (and (>= (Long/parseLong birthday-day) const/min-day) (<= (Long/parseLong birthday-day) const/max-day))
        [:birthday-day (str const/min-day "日から" const/max-day "日を選んでください")])
      (vali/rule (util/valid-date? birthday-year birthday-month birthday-day)
        [:birthday "正しい日付を入力してください"]))))

(defn- valid-business? [business-type business]
  (if (< 0 (Long/parseLong business-type))
    (do
      (vali/rule (or (not= 9999 (Long/parseLong business-type))
                   (and (= 9999 (Long/parseLong business-type)) (vali/has-value? business)))
        [:business "「職業」に「その他」を選んだ場合は、「職業(その他)」を入力してください"])
      (vali/rule (const/master-contains? (Long/parseLong business-type) const/business-list)
        [:business-type "*"]))))

(defn- valid-income? [income]
  (if (vali/has-value? income)
    (do
      (vali/rule (vali/valid-number? income)
        [:income "正しい数字を入力してください"])
      (if (not (vali/errors? :income))
        (let [income (Long/parseLong income)]
          (vali/rule (<= 0 income)
            [:income "収入がマイナス???"])
          (vali/rule (>= 1000000000 income)
            [:income "そんなに稼いでないでしょう?"]))))))

(defn valid-registration?
  "ユーザー登録バリデーション"
  [{:keys [nickname email password password-confirm gender
           birthday-year birthday-month birthday-day
           business-type business income] :as params}]
  ; 必須項目
  ; ニックネーム
  (valid-nickname? nickname)
  ; メールアドレス
  (valid-email? email)
  ; パスワード
  ; パスワード(確認)
  (valid-password? password password-confirm)
  ; 任意項目
  ; 性別
  (valid-gender? gender)
  ; 生年月日
  (valid-birthday? birthday-year birthday-month birthday-day)
  ; 職業
  (valid-business? business-type business)
  ; 年収
  (valid-income? income)

  (not (vali/errors?
         :nickname :email :password :password-confirm
         :gender :birthday :birthday-year :birthday-month :birthday-day
         :business-type :business :income)))

(defn handle-registration
  "ユーザー登録処理(POST)"
  [{:keys [nickname email password password-confirm gender
           birthday-year birthday-month birthday-day
           business-type business income] :as params}]
  ; バリデーション
  (if (valid-registration? params)
    (do
      ; tokenを作成する
      (let [token (lauth/create-random-token)
            limit-time (tcore/plus (util/now) (tcore/days 2))]
        ; データベース(users/actions)登録
        (transaction
          (let [hash (lauth/create-random-token)
                crypted-password (lauth/encrypt-password password)
                user-id (users/insert-user email crypted-password hash nickname const/user-registing false)
                birthday (util/date-time birthday-year birthday-month birthday-day)]
            (actions/insert-action token limit-time const/action-user-regist const/action-progress user-id email)
            (profiles/insert-profile user-id gender birthday income)
            (business/insert-business business-type user-id business)))
        ; メール送信
        (email/send-registration-email email token)
        ; メール送信完了画面表示(リダイレクト)
        (res/redirect (str "/regist_mail_completed?email=" email "&token=" token))))
    ; バリデーションエラー
    (auth-view/show-register params)))

(defn handle-registration-mail-complete
  "ユーザー登録確認メール送信完了"
  [{:keys [email token] :as params}]
  (if (empty? (actions/select-action-by-token token const/action-user-regist const/action-progress))
    (res/redirect "/")
    (auth-view/show-registration-mail-complete email token)))

(defn handle-registration-complete
  "ユーザー登録完了リンククリック"
  [{:keys [token] :as params}]
  (let [action (actions/select-action-by-token token const/action-user-regist const/action-progress)]
    (if (empty? action)
      (res/redirect "/")
      (let [user-id (action :user_id)]
        (transaction
          (actions/delete-action-by-token token)
          (users/update-user-status user-id const/user-normal))
        (let [user (users/select-user-by-id user-id const/user-normal)]
          (email/send-registed-user-email user-id (:nickname user))
          (lauth/login user)
          (session/flash-put! const/flash-message-success "ユーザー登録が完了しました")
          (res/redirect "/"))))))

(defn valid-login? [email password]
  (vali/rule (vali/has-value? email)
    [:email "メールアドレスを入力してください。"])
  (vali/rule (and (vali/min-length? email 3) (vali/max-length? email 255))
    [:email "メールアドレスは3文字以上255文字以下で入力してください"])
  (vali/rule (vali/is-email? email)
    [:email "メールアドレスの形式が間違っています"])
  (vali/rule (vali/has-value? password)
    [:password "パスワードを入力してください。"])
  (let [user (users/select-user-by-email email)]
    (if (empty? user)
      (vali/rule (not (empty? user))
        [:password "ユーザーかパスワードが間違っています"])
    (vali/rule (lauth/compare-password password (user :crypted_password))
      [:password "ユーザーかパスワードが間違っています"])))

  (not (vali/errors? :email :password)))

(defn handle-login
  "ログイン"
  [email password]
  ; バリデーション
  (if (valid-login? email password)
    (let [user (users/select-user-by-email email)]
          (lauth/login user)
          (res/redirect "/"))
    ; バリデーションエラー
    (do
      (lauth/logoff)
      (home-view/show nil))))

(defn handle-logoff
  "ログオフ"
  []
  (lauth/logoff)
  (res/redirect "/"))

(defn handle-request-reset-password
  "パスワード再設定要求(POST)"
  [{:keys [nickname email password password-confirm gender
           birthday-year birthday-month birthday-day
           business-type business income] :as params}]
  ; バリデーション
  (vali/rule (vali/has-value? email)
    [:email "メールアドレスを入力してください。"])
  (vali/rule (and (vali/min-length? email 3) (vali/max-length? email 255))
    [:email "メールアドレスは3文字以上255文字以下で入力してください"])
  (vali/rule (vali/is-email? email)
    [:email "メールアドレスの形式が間違っています"])
  (if (not (vali/errors? :email))
    (do
      (if (empty? (users/select-user-by-email email))
        ; ユーザが見つからなかったので、エラーも出さず完了画面へ
        (res/redirect (str "/request_reset_password_mail_complete?email=" email "&token=" (lauth/create-random-token)))
        ; tokenを作成する
        (let [token (lauth/create-random-token)
              limit-time (tcore/plus (util/now) (tcore/days 2))]
          ; データベース(actions)登録
          (transaction
            (let [hash (lauth/create-random-token)]
              (actions/insert-action token limit-time const/action-reset-password const/action-progress nil email)))
          ; メール送信
          (email/send-reset-password-email email token)
          ; メール送信完了画面表示(リダイレクト)
          (res/redirect (str "/request_reset_password_mail_complete?email=" email "&token=" token)))))
    ; バリデーションエラー
    (auth-view/show-request-reset-password params)))

(defn handle-request-reset-password-mail-complete
  "パスワード再設定メール送信完了"
  [{:keys [email token] :as params}]
  (auth-view/show-reset-password-mail-complete email token))

(defn handle-reset-password
  "パスワード再設定リンククリック"
  [{:keys [token] :as params}]
  (let [action (actions/select-action-by-token token const/action-reset-password const/action-progress)]
    (if (empty? action)
      (do
        (session/flash-put! const/flash-message-danger "リンクの有効期限が切れています")
        (res/redirect "/"))
      ; パスワード設定画面を表示
      (let [email (:note action)
            user (users/select-user-by-email email)
            user-id (:id user)]
        (auth-view/show-reset-password (assoc params :password "" :password-confirm ""))))))

(defn handle-reset-password-complete
  "パスワード再設定完了"
  [{:keys [token password password-confirm] :as params}]
  (let [action (actions/select-action-by-token token const/action-reset-password const/action-progress)]
    (if (empty? action)
      (do
        (session/flash-put! const/flash-message-danger "パスワードの再設定に失敗しました")
        (res/redirect "/"))
      ; パスワード設定画面を表示
      (let [email (:note action)
            user (users/select-user-by-email email)
            user-id (:id user)]
        ; バリデーション
        (if (valid-password? password password-confirm)
          (do
            (transaction
              (actions/delete-action-by-token token)
              (users/update-user-password user-id (lauth/encrypt-password password)))
            (lauth/login user)
            (session/flash-put! const/flash-message-success "パスワードを設定しました")
            (res/redirect "/"))
          ; バリデーションエラー
          (auth-view/show-reset-password params))))))

(defn handle-twitter-login
  "Twitter認証"
  [params]
  ; Twitterログイン
  (if-let [request-token (twitter/twitter-login "/twitter_callback")]
    ; Twitterへリダイレクト
    (res/redirect (.getAuthenticationURL request-token))
    ; 認証済
    (res/redirect "/")))

(defn handle-twitter-callback
  "Twitterコールバック"
  [{:keys [oauth_token oauth_verifier] :as params}]
  (if-let [provider-data (twitter/twitter-callback oauth_token oauth_verifier twitter/handle-registration-callback)]
    ; ユーザー登録
    (transaction
      (let [provider (:provider provider-data)
            twitter-user (:twitter-user provider-data)
            provider-user-id (:provider-user-id provider-data)
            nickname (:nickname provider-data)
            twitter-screen-name (:twitter-screen-name provider-data)
            provider-access-token (:provider-access-token provider-data)
            provider-access-token-secret (:provider-access-token-secret provider-data)
            hash (lauth/create-random-token)
            user-id (users/insert-user provider provider-user-id
                      provider-access-token provider-access-token-secret
                      hash nickname const/user-normal false)]
        (profiles/insert-twitter-profile user-id provider-user-id provider-access-token provider-access-token-secret)
        (business/insert-business "0" user-id nil)
        (email/send-registed-user-email user-id nickname)
        (auth-view/show-twitter-register (assoc provider-data :hash hash))))
    ; ログイン or 拒否
    (res/redirect "/")))

(defn valid-twitter-registration?
  "ユーザー登録バリデーション(Twitter)"
  [{:keys [hash nickname email gender
           birthday-year birthday-month birthday-day
           business-type business income] :as params}]
  ; 必須項目
  ; メールアドレス
  (valid-email2? email)
  ; 任意項目
  ; 性別
  (valid-gender? gender)
  ; 生年月日
  (valid-birthday? birthday-year birthday-month birthday-day)
  ; 職業
  (valid-business? business-type business)
  ; 年収
  (valid-income? income)

  (not (vali/errors?
         :email :gender
         :birthday :birthday-year :birthday-month :birthday-day
         :business-type :business :income)))

(defn handle-twitter-registration
  "ユーザー登録処理(POST)"
  [{:keys [hash nickname email gender
           birthday-year birthday-month birthday-day
           business-type business income] :as params}]
  ; バリデーション
  (if (valid-twitter-registration? params)
    (do
      (let [user (users/select-user-by-hash hash)
            user-id (:id user)
            birthday (util/date-time birthday-year birthday-month birthday-day)]
        (transaction
          (users/update-user-email user-id email)
          (profiles/update-profile user-id gender birthday income)
          (business/update-business business-type user-id business))
        (let [user (users/select-user-by-id user-id const/user-normal)]
          (lauth/login user)
          (session/flash-put! const/flash-message-success "ユーザー登録が完了しました")
          (res/redirect "/"))))
    ; バリデーションエラー
    (auth-view/show-twitter-register params)))

(defn valid-edit-profile?
  "プロフィール編集バリデーション"
  [{:keys [hash is-twitter
           nickname gender
           birthday-year birthday-month birthday-day
           business-type business income] :as params}]
  (do
    (if (not is-twitter)
      (valid-nickname? nickname))
    (and
      (not (vali/errors? :nickname))
      (valid-twitter-registration? params))))

(defn show-edit-profile
  "プロフィール編集画面表示"
  []
  (if-let [user (lauth/login?)]
    (let [user-id (:id user)
          profile (profiles/select-profile-by-user-id user-id)
          income (if (and profile (:income profile)) (str (.longValue (:income profile))) "")
          gender (if (and profile (:gender profile)) (str (:gender profile)) nil)
          business (business/select-business-by-user-id user-id)
          business-type (if business (str (:id business)) "")
          business (if (and business (:note business)) (:note business) "")
          birthday (if (and profile (:birthday profile)) (util/from-sql (:birthday profile)) nil)
          birthday-year (if birthday (str (tcore/year birthday)) nil)
          birthday-month (if birthday (str (tcore/month birthday)) nil)
          birthday-day (if birthday (str (tcore/day birthday)) nil)
          is-twitter (= (:provider user) "twitter")]
      (auth-view/edit-profile {:hash (:hash user), :is-twitter is-twitter,
                               :nickname (:nickname user), :gender gender,
                               :birthday-year birthday-year, :birthday-month birthday-month, :birthday-day birthday-day,
                               :business-type business-type, :business business, :income income}))
    (res/redirect "/")))

(defn handle-edit-profile
  "プロフィール編集(POST)"
  [{:keys [hash nickname gender
           birthday-year birthday-month birthday-day
           business-type business income] :as params}]
  ; バリデーション
  (if-let [user (lauth/login?)]
    (let [is-twitter (= (:provider user) "twitter")]
      (if (valid-edit-profile? (assoc params :is-twitter is-twitter))
        (do
          (let [user-id (:id user)
                birthday (util/date-time birthday-year birthday-month birthday-day)]
            (transaction
              (if (not is-twitter)
                (users/update-user-nickname user-id nickname))
              (profiles/update-profile user-id gender birthday income)
              (business/update-business business-type user-id business))
            (session/flash-put! const/flash-message-success "プロフィールを更新しました")
            (res/redirect "/")))
        ; バリデーションエラー
        (auth-view/edit-profile (assoc params :is-twitter is-twitter))))
    (res/redirect "/")))

(defn test-page
  "テストページ"
  []
  (layout/common
    "テストページ - 社畜.net"))

(defroutes auth-routes
  "ルーティング定義"
  (GET "/register" []
    (auth-view/show-register))
  (POST "/register" {params :params}
    (handle-registration params))
  (GET "/regist_mail_completed" {params :params}
    (handle-registration-mail-complete params))
  (GET "/regist_complete" {params :params}
    (handle-registration-complete params))
  (POST "/login" [email password]
    (handle-login email password))
  (POST "/logout" []
    (handle-logoff))
  (GET "/request_reset_password" []
    (auth-view/show-request-reset-password))
  (POST "/request_reset_password" {params :params}
    (handle-request-reset-password params))
  (GET "/request_reset_password_mail_complete" {params :params}
    (handle-request-reset-password-mail-complete params))
  (GET "/reset_password" {params :params}
    (handle-reset-password params))
  (POST "/reset_password" {params :params}
    (handle-reset-password-complete params))
  (GET "/delete-account" [] ; 未実装
    (test-page))
  (POST "/confirm-delete" [] ; 未実装
    (test-page))
  (GET "/edit_profile" []
    (show-edit-profile))
  (POST "/edit_profile" {params :params}
    (handle-edit-profile params))
  (GET "/twitter_login" {params :params}
    (handle-twitter-login params))
  (GET "/twitter_callback" {params :params}
    (handle-twitter-callback params))
  (POST "/twitter_register" {params :params}
    (handle-twitter-registration params)))
