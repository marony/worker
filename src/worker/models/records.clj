(ns worker.models.records
  (:require [clojure.java.jdbc :as sql]
            [korma.db :refer [defdb transaction]]
            [korma.core :refer :all]
            [worker.const :as const]
            [worker.lib.util :as util]
            [clj-time.core :as tcore]
            [worker.models.db :as db])
  (:use [carica.core]))

(defdb korma-db (config :db))

(defn select-record-by-date
  "ユーザーIDと日付から記録を検索する"
  [user-id record-date]
  (first (select :records
           (where {:user_id user-id, :record_date (util/to-sql record-date)}))))

(defn select-prev-record-by-date
  "ユーザーIDと日付から1レコード古い記録を検索する"
  [user-id record-date]
  (first (select :records
           (where {:user_id user-id, :record_date [< (util/to-sql record-date)]})
           (order :record_date :desc)
           (limit 1))))

(defn select-next-record-by-date
  "ユーザーIDと日付から1レコード新しい記録を検索する"
  [user-id record-date]
  (first (select :records
           (where {:user_id user-id, :record_date [> (util/to-sql record-date)]})
           (order :record_date :asc)
           (limit 1))))

(defn select-coming-record
  "ユーザーIDから出社して退社していない最新の記録を検索する"
  [user-id]
  (first (select :records
           (where {:user_id user-id, :come_time [not= nil] :leave_time [= nil]})
           (order :record_date :desc)
           (limit 1))))

(defn select-near-records
  "今日と前後直近の1レコードを取得"
  [user-id record-date]
  (list
    ; TODO: 3回クエリを投げてるので1回で取ってくるように
    (select-prev-record-by-date user-id record-date)
    (select-record-by-date user-id record-date)
    (select-next-record-by-date user-id record-date)))

(defn select-records-by-period
  "期間内の記録とユーザを取得(新しい順)"
  ; ユーザ指定
  ([user-id start-date end-date]
    (select :records
      (where {:record_date [>= (util/to-sql start-date)]})
      (where {:record_date [<= (util/to-sql end-date)]})
      (where {:come_time [not= nil], :leave_time [not= nil]})
      (where {:user_id user-id})
      (order :record_date :desc)))
  ; 全ユーザ
  ([start-date end-date]
    (select :records
      (join :users (= :users.id :user_id))
      (join :profiles (= :profiles.user_id :user_id))
      (join :business (= :business.user_id :user_id))
      (fields :id :record_date :user_id :come_time :leave_time :note
        :users.hash :users.nickname
        :profiles.gender :profiles.birthday :profiles.income
        [:business.id :business_id] [:business.note :business_note])
      (where {:record_date [>= (util/to-sql start-date)]})
      (where {:record_date [<= (util/to-sql end-date)]})
      (where {:come_time [not= nil], :leave_time [not= nil]})
      (where {:users.status const/user-normal})
      ; さくらユーザを除く条件
;      (where {:users.id [> 0]})
      (order :record_date :desc))))

(defn delete-record-by-date
  "ユーザーIDと日付から記録を削除する"
  [user-id record-date]
  (let [record-sql-date (util/to-sql record-date)]
    (delete :records
      (where {:user_id user-id, :record_date record-sql-date}))))

(defn insert-sakura-records
  "さくらユーザの記録をする"
  []
  (dorun
    (for [day (range -40 0)]
      (let [date (tcore/plus (util/today) (tcore/days day))]
        (if (< (tcore/day-of-week date) 6)
          (let [date1 date
                 date (util/to-sql date1)]
            (transaction
              (let [records (select :records
                              (where {:user_id [< 0]
                                      :record_date date}))]
                (if (empty? records)
                  (dorun
                    (for [user (select :users
                                 (where {:sakura true
                                         :status const/user-normal}))]
                      (let [now (util/now)
                            come-time (util/to-sql
                                        (util/from-local-date-time
                                          (tcore/date-time
                                            (tcore/year date1) (tcore/month date1) (tcore/day date1)
                                            (+ 7 (rand-int 4)) (rand-int 60))))
                            leave-time (util/to-sql
                                         (util/from-local-date-time
                                           (tcore/date-time
                                             (tcore/year date1) (tcore/month date1) (tcore/day date1)
                                             (+ 16 (rand-int 8)) (rand-int 60))))
                            now (util/to-sql now)]
                        (insert :records
                          (values {:user_id (:id user), :record_date date,
                                   :come_time come-time, :leave_time leave-time,
                                   :created_at now, :updated_at now}))))))))))))))

; TODO: upsert-record, upsert-record-come, upsert-record-leaveをまとめる
(defn upsert-record-come
  "出社記録を挿入 or 更新する"
  [user-id record-date come-time]
  (insert-sakura-records)
  (transaction
    (let [now (util/to-sql (util/now))
          record (select-record-by-date user-id record-date)
          record-sql-date (util/to-sql record-date)
          come-sql-time (if come-time (util/to-sql come-time) nil)]
      (if (empty? record)
        (if (not (nil? come-time))
          ; 挿入
          (insert :records
            (values {:user_id user-id, :record_date record-sql-date,
                     :come_time come-sql-time,
                     :created_at now, :updated_at now})))
        ; 更新 or 削除
        (if (nil? come-time)
          ; 削除
          (delete-record-by-date user-id record-date)
          ; 更新
          (update :records
            (set-fields {:come_time come-sql-time, :updated_at now})
            (where {:user_id user-id, :record_date record-sql-date})))))))

(defn upsert-record-leave
  "退社記録を挿入 or 更新する"
  [user-id record-date leave-time]
  (insert-sakura-records)
  (transaction
    (let [now (util/to-sql (util/now))
          record (select-record-by-date user-id record-date)
          record-sql-date (util/to-sql record-date)
          leave-sql-time (if leave-time (util/to-sql leave-time) nil)]
      ; 更新
      (update :records
        (set-fields {:leave_time leave-sql-time, :updated_at now})
        (where {:user_id user-id, :record_date record-sql-date})))))

(defn upsert-record
  "記録を挿入 or 更新する"
  [user-id record-date come-time leave-time note]
  (insert-sakura-records)
  (transaction
    (let [now (util/to-sql (util/now))
          record (select-record-by-date user-id record-date)
          record-sql-date (util/to-sql record-date)
          come-sql-time (if come-time (util/to-sql come-time) nil)
          leave-sql-time (if leave-time (util/to-sql leave-time) nil)]
      (if (empty? record)
        (if (not (nil? come-time))
          ; 挿入
          (:id (insert :records
                 (values {:user_id user-id, :record_date record-sql-date,
                          :come_time come-sql-time, :leave_time leave-sql-time, :note note,
                          :created_at now, :updated_at now}))))
        ; 更新 or 削除
        (if (and (nil? come-time) (nil? leave-time))
          ; 削除
          (do
            (delete-record-by-date user-id record-date)
            nil)
          ; 更新
          (do
            (update :records
              (set-fields {:come_time come-sql-time, :leave_time leave-sql-time, :note note, :updated_at now})
              (where {:user_id user-id, :record_date record-sql-date}))
            (:id record)))))))
