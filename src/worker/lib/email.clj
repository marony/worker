(ns worker.lib.email
  (:require [worker.const :as const])
  (:use [carica.core])
  (:import (org.apache.commons.mail SimpleEmail))
  (:import (org.apache.commons.mail DefaultAuthenticator)))

(defn send-email
  "メールを送信する"
  [to subject content]
  (let [email (SimpleEmail.)]
    (doto email
      (.setSmtpPort (config :smtp-port))
      (.setAuthenticator (DefaultAuthenticator. (config :smtp-user-name) (config :smtp-password)))
      (.setHostName (config :smtp-host-name))
      (.setFrom (config :mail-from))
      (.setCharset "iso-2022-jp")
      (.setContent content "text/plain; charset=iso-2022-jp")
      (.setSubject subject)
      (.addTo to)
      (.setTLS (config :smtpTLS)))
    (.send email)))

(defn send-registration-email
  "ユーザー登録メールを送信する"
  [to token]
  (->> (str const/site-name "に登録するには以下のURLをクリックしてください。\n\n" (config :site-url)
         "/regist_complete?token=" token "\n\n" "【" const/site-name "】" (config :site-url) "/")
    (send-email to (str "【" const/site-name "】 " "ユーザー登録確認"))))

(defn send-reset-password-email
  "パスワード再設定メールを送信する"
  [to token]
  (->> (str const/site-name "のパスワードを再設定するには以下のURLをクリックしてください。\n\n" (config :site-url)
         "/reset_password?token=" token "\n\n" "【" const/site-name "】" (config :site-url) "/")
    (send-email to (str "【" const/site-name "】 " "パスワード再設定"))))

(defn send-registed-user-email
  "ユーザー登録されたことをメールで自分に知らせる"
  [user-id nickname]
  (->> (str "ユーザーが登録しました。[" user-id "], [" nickname "]")
    (send-email "marony@binbo-kodakusan.com" (str "【" const/site-name "】 " "ユーザー登録完了"))))
