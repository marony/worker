(ns worker.const
  (:require [clj-time.local :as time]
            [clj-time.core :as tcore]))

;;; サイト名
(def site-name "社畜.net")

;;; 日付系
(def min-year 1950)
(defn max-year []
  (tcore/year (tcore/today)))
(def min-month 1)
(def max-month 12)
(def min-day 1)
(def max-day 31)
(def min-record-date (tcore/date-time 2000 01 01))

(defn year-options []
  (conj (map (fn [n] [n n]) (range min-year (inc (max-year)))) ["なし" 0]))
(defn month-options []
  (conj (map (fn [n] [n n]) (range min-month (inc max-month))) ["なし" 0]))
(defn day-options []
  (conj (map (fn [n] [n n]) (range min-day (inc max-day))) ["なし" 0]))

;;; マスター保持用レコード
(defrecord MasterValue [id symbol name])

;;; マスター検索関数
(defmulti search-master
  #(class %2))
;;; idから検索
(defmethod search-master Long [col key]
  (->> col
    (drop-while #(not= key (:id %)))
    (first)))
;;; symbolから検索
(defmethod search-master clojure.lang.Keyword [col key]
  (->> col
    (drop-while #(not= key (:symbol %)))
    (first)))
;;; nameから検索
(defmethod search-master String [col key]
  (->> col
    (drop-while #(not= key (:name %)))
    (first)))

(defn master-contains? [id list]
  (some #(= (:id %) id) list))

;;; さくらユーザ一覧
(def name-list ["さくら" "ゆき" "ごんぞー" "sochi" "こん" "みっち" "yukiko" "さっぱり" "あつみ" "きんこ"
                "zoffi" "yatta" "ふるー" "Google" "Yahoo" "じん" "Wachi" "ごろ" "うみ" "かち"
                "エロ" "Dacchi" "Local" "Clojure" "C#" "F#" "ぷる" "AAA" "ZZZ" "Mr. X"
                "XXX" "T" "たっち" "たくろー" "わすん" "もも" "わたる" "びれり" "ゆきち" "さくろ〜"
                "きむたく" "ごろぅ" "のっち" "ゴリラ" "Quii" "Kawaii" "まもる" "りょう" "すしろー" "クララ"
                "ずし" "都民" "津軽付近" "社畜王" "死者" "IT王者" "千葉市民" "くっち" "ぼんびー" "だんぼ"
                "こもどーろ" "Apple儲" "iPhoneゲーマー" "スマホ惰眠" "Android大好き" "だっちわいふ" "銀座" "ぜろ" "ぜふぃ" "00AAA"
                "☆☆☆" "♪" "げんご" "Haskeller"])
;;; 性別一覧
(def gender-list
  [(->MasterValue 0 :none "未入力")
   (->MasterValue 1 :male "男性")
   (->MasterValue 2 :female "女性")
   (->MasterValue 3 :bisectual_male "心は女")
   (->MasterValue 4 :bisectual_female "心は男")
   (->MasterValue 5 :unisextual "両性")
   (->MasterValue 99 :unknown "不明")])

;;; 職業一覧(複数選択可)
(def business-list
  [(->MasterValue 0 :none "未入力")
   (->MasterValue 1 :jobless "自宅警備員")
   (->MasterValue 10 :tester "テスター")
   (->MasterValue 11 :coder "コーダー")
   (->MasterValue 12 :debugger "デバッガー")
   (->MasterValue 13 :pg "プログラマー")
   (->MasterValue 14 :se "SE")
   (->MasterValue 15 :pl "PL")
   (->MasterValue 16 :pm "PM")
   (->MasterValue 20 :boss "社長")
   (->MasterValue 21 :officer "役員")
   (->MasterValue 22 :sales "営業")
   (->MasterValue 23 :publicity "広報")
   (->MasterValue 24 :deskwork "事務")
   (->MasterValue 30 :designer "デザイナー")
   (->MasterValue 31 :web-designer "Webデザイナー")
   (->MasterValue 32 :3d-designer "3Dデザイナー")
   (->MasterValue 33 :eshi "絵師")
   (->MasterValue 40 :game-designer "ゲームデザイナー")
   (->MasterValue 41 :producer "プロデューサー")
   (->MasterValue 43 :director "ディレクター")
   (->MasterValue 44 :senario-writer "シナリオライター")
   (->MasterValue 50 :writer "作家")
   (->MasterValue 60 :comic-writer "漫画家")
   (->MasterValue 9999 :other "その他")])

;;; フラッシュのキー
(def flash-message-success "message-success")
(def flash-message-info "message-info")
(def flash-message-warning "message-warning")
(def flash-message-danger "message-danger")

;;; セッションのキー
(def session-login "login")

;;; ユーザーの状態
;;; 不正
(def user-invalid 0)
;;; 通常
(def user-normal 1)
;;; 登録中
(def user-registing 8)
;;; 停止
(def user-stop 9)

;;; アクションの種別
;;; ユーザー登録
(def action-user-regist 1)
;;; パスワード再発行
(def action-reset-password 2)

;;; アクションの状態
;;; 続行中
(def action-progress 1)
;;; 完了
(def action-completed 2)

; ランキング種別
; 勤務時間ランキング
(def ranking-type-working-hours 1)
; 時給ランキング
(def ranking-type-hourly-wage 2)
; 連続出勤ランキング
(def ranking-type-continuous-working 3)
; 休日出勤ランキング
(def ranking-type-off-day-working 4)
; 深夜残業ランキング
(def ranking-type-midnight-working-minutes 5)
; 深夜と見なす時間
(def midnight-hour 22)

; ランキング種別一覧
(def ranking-types [{:type ranking-type-working-hours
                     :title "勤務時間ランキング"
                     :name "偏差値" :value :deviation
                     :description "過去30日間の勤務時間ランキング"}
                    {:type ranking-type-hourly-wage
                     :title "時給ランキング"
                     :name "偏差値" :value :deviation
                     :description "過去30日間の時給ランキング"}
                    {:type ranking-type-continuous-working
                     :title "連続出勤ランキング"
                     :name "日数" :value :continuous-working-days
                     :description "現在の連続出勤中ランキング"}
                    {:type ranking-type-off-day-working
                     :title "休日出勤ランキング"
                     :name "日数" :value :off-day-working-days
                     :description "過去30日間の休日出勤数ランキング"}
                    {:type ranking-type-midnight-working-minutes
                     :title "深夜残業ランキング"
                     :name "偏差値" :value :deviation
                     :description "過去30日間の深夜残業ランキング"}
                    ])

; 表示するランキング(上下)の数
(def ranking-limit 5)
