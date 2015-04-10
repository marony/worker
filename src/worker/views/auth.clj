(ns worker.views.auth
  (:require [hiccup.page :as page]
            [hiccup.element :as element]
            [hiccup.form :as form]
            [noir.validation :as vali]
            [worker.views.layout :as layout]
            [worker.views.parts :as parts]
            [worker.lib.util :as util]
            [worker.lib.auth :as lib-auth]
            [worker.const :as const]
            [taoensso.timbre :as tim])
  (:use [carica.core]))

(defn error-message
  "エラーメッセージ"
  [[first-error]]
  [:div.label.label-danger first-error])

(defn- create-gender
  "性別ラジオボタン作成"
  [gender]
  (for [gender-value const/gender-list]
    [(if (vali/errors? :gender) :div.col-sm-1.has-error :div.col-sm-1.has-success)
     [:label { :class "control-label" }
      (form/radio-button { :class "form-control" } "gender" (= (util/string-to-long gender) (:id gender-value)) (:id gender-value)) (:name gender-value)]]))

(defn- create-birthday
  "誕生日リスト作成"
  [birthday-type value print-name]
  [:div (if (or (vali/errors? :birthday) (vali/errors? birthday-type)) {:class "col-sm-3 has-error"} {:class "col-sm-3 has-success"})
   [:div.input-group
    [:select {:class "form-control" :name (name birthday-type)}
     (form/select-options
       (condp = birthday-type
         :birthday-year (const/year-options)
         :birthday-month (const/month-options)
         :birthday-day (const/day-options))
       (util/string-to-long value))]
    [:span.input-group-addon print-name]]
   (condp = birthday-type
     :birthday-year (vali/on-error :birthday-year error-message)
     :birthday-month (vali/on-error :birthday-month error-message)
     :birthday-day (vali/on-error :birthday-day error-message))])

(defn- create-business
  "職業リスト作成"
  [business-type]
  [(if (vali/errors? :business-type) :div.col-sm-3.has-error :div.col-sm-3.has-success)
   [:select {:class "form-control" :name "business-type"}
    (form/select-options (util/master-to-options const/business-list) (util/string-to-long business-type))]
   (vali/on-error :business-type error-message)])

(defn show-register
  "ユーザー登録フォーム定義"
  [& [{:keys [nickname email password password-confirm gender
              birthday-year birthday-month birthday-day
              business-type business income] :as param}]]
  (layout/common
    (str "ユーザー登録 - " const/site-name)
    [:div.container
     [:div.col-sm-2.col-sm-offset-10
      (element/link-to "/twitter_login" [:button.btn.btn-twitter [:i.fa.fa-twitter] " | Twitterで登録"] )]
     [:h1 "ユーザー登録"]
     (form/form-to { :class "form-horizontal" } [:post "/register"]
       [:div.well
        [:h4 "必須項目"]
        ; ニックネーム
        [:div.form-group
         [(if (vali/errors? :nickname) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "nickname" "ニックネーム：")]
         [(if (vali/errors? :nickname) :div.col-sm-10.has-error :div.col-sm-10.has-success)
          (form/text-field {:class "form-control" :placeholder "ニックネーム" :required true :autofocus true } "nickname" nickname)
          (vali/on-error :nickname error-message)]]
        ; Eメール
        [:div.form-group
         [(if (vali/errors? :email) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "email" "Eメール：")]
         [(if (vali/errors? :email) :div.col-sm-10.has-error :div.col-sm-10.has-success)
          (form/email-field {:class "form-control" :placeholder "Eメール" :required true } "email" email)
          (vali/on-error :email error-message)]]
        ; パスワード
        [:div.form-group
         [(if (vali/errors? :password) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "password" "パスワード：")]
         [(if (vali/errors? :password) :div.col-sm-10.has-error :div.col-sm-10.has-success)
          (form/password-field {:class "form-control" :placeholder "パスワード" :required true } "password" password)
          (vali/on-error :password error-message)]]
        ; パスワード(確認)
        [:div.form-group
         [(if (vali/errors? :password-confirm) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "password-confirm" "パスワード(確認)：")]
         [(if (vali/errors? :password-confirm) :div.col-sm-10.has-error :div.col-sm-10.has-success)
          (form/password-field {:class "form-control" :placeholder "パスワード(確認)" :required true } "password-confirm" password-confirm)
          (vali/on-error :password-confirm error-message)]]]
       [:div.well
        [:h4 "任意項目"]
        ; 性別
        [:div.form-group
         [(if (vali/errors? :gender) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "gender" "性別：")]
         (create-gender gender)
         (vali/on-error :gender error-message)]
        ; 誕生日
        [:div.form-group
         [(if (vali/errors? :birthday) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "birthday" "誕生日(西暦)：")]
         (create-birthday :birthday-year birthday-year "年")
         (create-birthday :birthday-month birthday-month "月")
         (create-birthday :birthday-day birthday-day "日")]
        [:div.form-group
         [:div.col-sm-10.col-sm-offset-2
         (vali/on-error :birthday error-message)]]
        ; 職業
        [:div.form-group
         [(if (vali/errors? :business-type) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "business" "(メインの)職業：")]
         (create-business business-type)
         [(if (vali/errors? :business) :div.col-sm-7.has-error :div.col-sm-7.has-success)
          (form/text-field {:class "form-control" :placeholder "職業(その他)" } "business" business)
          (vali/on-error :business error-message)]]
        ; 年収
        [:div.form-group
         [(if (vali/errors? :income) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "income" "年収(円)：")]
         [:div.col-sm-10
          [(if (vali/errors? :income) :div.input-group.has-error :div.input-group.has-success)
           (form/text-field {:class "form-control" :placeholder "年収(円)" } "income" income)
           [:span.input-group-addon "円"]]]]
        [:div.form-group
         [:div.col-sm-10.col-sm-offset-2
          (vali/on-error :income error-message)]
         [:div.col-sm-12
          [:label.label.label-warning "ランキングを計算するのに使います。年収や時給は公開されません。"]]]]
       ; 登録ボタン
       [:div.form-group
        [:div.col-sm-2.col-sm-offset-5
         (form/submit-button { :class "form-control btn-primary" } "ユーザー登録する")]])]))

(defn show-registration-mail-complete
  "ユーザー登録確認メール送信完了"
  [email token]
  (layout/common
    (str "ユーザー登録確認メール送信完了 - " const/site-name)
    [:div.container
     [:div.jumbotron
      [:h3.text-muted "ユーザー登録確認メール送信完了"]
      [:div.well
       (str email "にユーザー登録確認メールを送信しました。メールのURLをクリックし、登録を完了してください。")]]]))

(defn show-request-reset-password
  "パスワード再設定"
  [& [{:keys [email] :as param}]]
  (layout/common
    (str "パスワード再設定 - " const/site-name)
    [:div.container
     [:h1 "パスワード再設定"]
     (form/form-to { :class "form-horizontal" } [:post "/request_reset_password"]
       [:div.well
        ; Eメール
        [:div.form-group
         [(if (vali/errors? :email) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "email" "Eメール：")]
         [(if (vali/errors? :email) :div.col-sm-10.has-error :div.col-sm-10.has-success)
          (form/email-field {:class "form-control" :placeholder "Eメール" :required true } "email" email)
          (vali/on-error :email error-message)]]
        ; 登録ボタン
        [:div.form-group
         [:div.col-sm-2.col-sm-offset-5
          (form/submit-button { :class "form-control btn-primary" } "メールを送信する")]]])]))

(defn show-reset-password-mail-complete
  "パスワード再設定メール送信完了"
  [email token]
  (layout/common
    (str "パスワード再設定メール送信完了 - " const/site-name)
    [:div.container
     [:div.jumbotron
      [:h3.text-muted "ユーザー登録確認メール送信完了"]
      [:div.well
       (str email "にパスワード再設定メールを送信しました。メールのURLをクリックし、パスワードを再設定してください。")]]]))

(defn show-reset-password
  "パスワード再設定"
  [& [{:keys [token password password-confirm] :as param}]]
  (layout/common
    (str "パスワード再設定 - " const/site-name)
    [:div.container
     [:h1 "パスワード再設定"]
     (form/form-to { :class "form-horizontal" } [:post "/reset_password"]
       (form/hidden-field "token" token)
       [:div.well
        ; パスワード
        [:div.form-group
         [(if (vali/errors? :password) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "password" "パスワード：")]
         [(if (vali/errors? :password) :div.col-sm-10.has-error :div.col-sm-10.has-success)
          (form/password-field {:class "form-control" :placeholder "パスワード" :required true } "password" password)
          (vali/on-error :password error-message)]]
        ; パスワード(確認)
        [:div.form-group
         [(if (vali/errors? :password-confirm) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "password-confirm" "パスワード(確認)：")]
         [(if (vali/errors? :password-confirm) :div.col-sm-10.has-error :div.col-sm-10.has-success)
          (form/password-field {:class "form-control" :placeholder "パスワード(確認)" :required true } "password-confirm" password-confirm)
          (vali/on-error :password-confirm error-message)]]]
        ; 登録ボタン
        [:div.form-group
         [:div.col-sm-2.col-sm-offset-5
          (form/submit-button { :class "form-control btn-primary" } "パスワードを設定する")]])]))

(defn show-twitter-register
  "ユーザー登録フォーム定義(Twitter)"
  [& [{:keys [hash
              nickname email gender
              birthday-year birthday-month birthday-day
              business-type business income] :as param}]]
  (layout/common
    (str "ユーザー登録 - " const/site-name)
    [:div.container
     [:h1 "ユーザー登録"]
     (form/form-to { :class "form-horizontal" } [:post "/twitter_register"]
       (form/hidden-field "hash" hash)
       (form/hidden-field "nickname" nickname)
       [:div.well
        "ユーザー情報"
        ; ニックネーム
        [:div.form-group
          [:div.col-sm-2.has-success (form/label {:class "control-label"} "nickname" "ニックネーム：")]
          [:div.col-sm-10.has-success (form/label {:class "form-control"} "nickname" nickname)]]
        ; Eメール
        [:div.form-group
         [(if (vali/errors? :email) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "email" "Eメール：")]
         [(if (vali/errors? :email) :div.col-sm-10.has-error :div.col-sm-10.has-success)
          (form/email-field {:class "form-control" :placeholder "Eメール"} "email" email)
          (vali/on-error :email error-message)]]]
       [:div.well
        [:h4 "任意項目"]
        ; 性別
        [:div.form-group
         [(if (vali/errors? :gender) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "gender" "性別：")]
         (create-gender gender)
         (vali/on-error :gender error-message)]
        ; 誕生日
        [:div.form-group
         [(if (vali/errors? :birthday) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "birthday" "誕生日(西暦)：")]
         (create-birthday :birthday-year birthday-year "年")
         (create-birthday :birthday-month birthday-month "月")
         (create-birthday :birthday-day birthday-day "日")]
        [:div.form-group
         [:div.col-sm-10.col-sm-offset-2
          (vali/on-error :birthday error-message)]]
        ; 職業
        [:div.form-group
         [(if (vali/errors? :business-type) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "business" "(メインの)職業：")]
         (create-business business-type)
         [(if (vali/errors? :business) :div.col-sm-7.has-error :div.col-sm-7.has-success)
          (form/text-field {:class "form-control" :placeholder "職業(その他)" } "business" business)
          (vali/on-error :business error-message)]]
        ; 年収
        [:div.form-group
         [(if (vali/errors? :income) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "income" "年収(円)：")]
         [:div.col-sm-10
          [(if (vali/errors? :income) :div.input-group.has-error :div.input-group.has-success)
           (form/text-field {:class "form-control" :placeholder "年収(円)" } "income" income)
           [:span.input-group-addon "円"]]]]
        [:div.form-group
         [:div.col-sm-10.col-sm-offset-2
          (vali/on-error :income error-message)]
         [:div.col-sm-12
          [:label.label.label-warning "ランキングを計算するのに使います。年収や時給は公開されません。"]]]]
       ; 登録ボタン
       [:div.form-group
        [:div.col-sm-2.col-sm-offset-5
         (form/submit-button { :class "form-control btn-primary" } "ユーザー登録する")]])]))

(defn edit-profile
  "プロフィール編集フォーム定義"
  [& [{:keys [hash is-twitter
              nickname gender
              birthday-year birthday-month birthday-day
              business-type business income] :as param}]]
  (layout/common
    (str "プロフィール編集 - " const/site-name)
    [:div.container
     [:h1 "プロフィール編集"]
     (form/form-to { :class "form-horizontal" } [:post "/edit_profile"]
       (form/hidden-field "hash" hash)
       (if (not is-twitter)
         (list
           [:div.well
            "ユーザー情報"
            ; ニックネーム
            [:div.form-group
             [(if (vali/errors? :nickname) :div.col-sm-2.has-error :div.col-sm-2.has-success)
              (form/label {:class "control-label"} "nickname" "ニックネーム：")]
             [(if (vali/errors? :nickname) :div.col-sm-10.has-error :div.col-sm-10.has-success)
              (form/text-field {:class "form-control" :placeholder "ニックネーム" :required true :autofocus true } "nickname" nickname)
              (vali/on-error :nickname error-message)]]]))
       [:div.well
        [:h4 "任意項目"]
        ; 性別
        [:div.form-group
         [(if (vali/errors? :gender) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "gender" "性別：")]
         (create-gender gender)
         (vali/on-error :gender error-message)]
        ; 誕生日
        [:div.form-group
         [(if (vali/errors? :birthday) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "birthday" "誕生日(西暦)：")]
         (create-birthday :birthday-year birthday-year "年")
         (create-birthday :birthday-month birthday-month "月")
         (create-birthday :birthday-day birthday-day "日")]
        [:div.form-group
         [:div.col-sm-10.col-sm-offset-2
          (vali/on-error :birthday error-message)]]
        ; 職業
        [:div.form-group
         [(if (vali/errors? :business-type) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "business" "(メインの)職業：")]
         (create-business business-type)
         [(if (vali/errors? :business) :div.col-sm-7.has-error :div.col-sm-7.has-success)
          (form/text-field {:class "form-control" :placeholder "職業(その他)" } "business" business)
          (vali/on-error :business error-message)]]
        ; 年収
        [:div.form-group
         [(if (vali/errors? :income) :div.col-sm-2.has-error :div.col-sm-2.has-success)
          (form/label {:class "control-label"} "income" "年収(円)：")]
         [:div.col-sm-10
          [(if (vali/errors? :income) :div.input-group.has-error :div.input-group.has-success)
           (form/text-field {:class "form-control" :placeholder "年収(円)" } "income" income)
           [:span.input-group-addon "円"]]]]
        [:div.form-group
         [:div.col-sm-10.col-sm-offset-2
          (vali/on-error :income error-message)]
         [:div.col-sm-12
          [:label.label.label-warning "ランキングを計算するのに使います。年収や時給は公開されません。"]]]]
       ; 登録ボタン
       [:div.form-group
        [:div.col-sm-2.col-sm-offset-5
         (form/submit-button { :class "form-control btn-primary" } "更新する")]])]))