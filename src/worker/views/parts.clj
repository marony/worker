(ns worker.views.parts
  (:require [worker.lib.util :as util]
            [worker.const :as const]
            [worker.models.records :as records]
            [clj-time.core :as tcore]
            [hiccup.form :as form]
            [hiccup.element :as element]
            [noir.validation :as vali]))

(defn error-message [[first-error]]
  "エラーメッセージ"
  [:div.label.label-danger first-error])

(defn login-form
  "ログインフォーム定義"
  [user & data]
  (if (and user data)
    ; ログイン時
    (list
      ; ビューからデータを取得するのは嫌だけど…
      (let [user-id (:id user)
            ranking_ (nth (:ranking-data (nth data 0)) (dec const/ranking-type-hourly-wage) [])
            ranking (nth ranking_ 1 [])
            user-rank (first (filter #(= (:id user) (:user_id %)) ranking))]
        [:div.well
         [:h5.text-muted (str (:nickname user) " さん")]
         [:row
          [:label.label.label-info.col-sm-12
           (if user-rank
             (list
               (str (quot (:working-time user-rank) 60) "時間勤務"))
             (list
               "0時間勤務"))]]
         [:row
          [:label.label.label-info.col-sm-12
           (if user-rank
             (list
               (str "時給" (Math/round (:hourly-wage user-rank)) "円"))
             (list
               "時給:測定不能"))]]
         (form/form-to { :class "form" } [:post "/logout"]
           [:div.form-group
            (form/submit-button {:class "form-control"} "ログオフ")])
         (element/link-to {} "/edit_profile" "プロフィール編集")]))
    ; 未ログイン時
    (list
      [:div.well
       [:row
        (form/form-to { :class "form form-signin" } [:post "/login"]
          [:div.form-group
           [(if (vali/errors? :email) :div.has-error :div.has-success)
            (form/email-field {:placeholder "Eメール" :class "form-control" :required true :autofocus true } "email")]
           (vali/on-error :email error-message)]
          [:div.form-group
           [(if (vali/errors? :password) :div.has-error :div.has-success)
            (form/password-field {:placeholder "パスワード" :class "form-control" :required true } "password")]
           (vali/on-error :password error-message)]
          [:div.form-group
           (form/submit-button {:class "form-control btn-primary"} "ログイン")])]
       [:row
        (element/link-to "/twitter_login" [:button.btn.btn-twitter [:i.fa.fa-twitter] " | 登録/ログイン"])]]
      [:row
       [:p.text-muted (element/link-to "/request_reset_password" "パスワードを忘れた？")]])))
