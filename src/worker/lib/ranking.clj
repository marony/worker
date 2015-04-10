(ns worker.lib.ranking
  (:require [worker.models.records :as records]
            [worker.lib.util :as util]
            [worker.const :as const]
            [clj-time.core :as tcore]
            [clojure.math.numeric-tower :as nt]
            [taoensso.timbre :as tim]))

(defn calc-working-time
  "勤務時間(分)を求める"
  [come-time leave-time]
  (try
    (tcore/in-minutes
      (tcore/interval come-time leave-time))
    (catch IllegalArgumentException _ 0)))

(defn holiday?
  "週末(休日)かどうか判断する"
  [date]
  (let [day-of-week (tcore/day-of-week date)]
    (or (= day-of-week 6) (= day-of-week 7))))

(defn calc-summary-by-user
  "ユーザごとの勤務時間(分)・連続出勤日数・休日出勤日数を求める"
  [acc r]
  (let [today (util/today)
         yesterday (tcore/plus today (tcore/days -1))
        user-id (:user_id r)
        come-time (util/from-sql (:come_time r))
        leave-time (util/from-sql (:leave_time r))
        record-date (util/from-sql (:record_date r))
        r (dissoc r :id :record_date :come_time :leave_time)
        minutes (calc-working-time come-time leave-time)]
    (if (contains? acc user-id)
      ; 2レコード目以降
      (let [user-record (acc user-id)
            ; 勤務時間を加算
            working-time (+ (:working-time user-record) minutes)
            prev-day (:prev-day user-record)
            prev-day (if (and prev-day (= prev-day (tcore/plus record-date (tcore/days 1))))
                       prev-day
                       nil)
            ; 連続出勤日数
            continuous-working-days (:continuous-working-days user-record)
            continuous-working-days (if prev-day
                                      ; 前日も出勤しているので加算
                                      (inc continuous-working-days)
                                      ; 記録が途切れた
                                      continuous-working-days)
            ; 休日出勤日数
            off-day-working-days (:off-day-working-days user-record)
            off-day-working-days (if (holiday? record-date)
                                   ; 休日なので加算
                                   (inc off-day-working-days)
                                   ; 平日
                                   off-day-working-days)
            ; 深夜残業時間
            leave-hour (tcore/hour leave-time)
            midnight (util/from-local-date-time
                       (tcore/date-time
                         (tcore/year record-date) (tcore/month record-date) (tcore/day record-date)
                         (dec const/midnight-hour) 59 59))
            midnight-working-minutes (:midnight-working-minutes user-record)
            midnight-working-minutes (if (tcore/before? midnight leave-time)
                                       (+ midnight-working-minutes (tcore/in-minutes (tcore/interval midnight leave-time)))
                                       midnight-working-minutes)]
        (assoc acc user-id (assoc r
                             :prev-day (if prev-day record-date nil)
                             :working-time working-time
                             :continuous-working-days continuous-working-days
                             :off-day-working-days off-day-working-days
                             :midnight-working-minutes midnight-working-minutes)))
      ; 最初のレコード
      (let [prev-day (if (= record-date yesterday)
                       yesterday
                       (if (= record-date today)
                         today
                         nil))
            continuous-working-day (if prev-day 1 0)
            midnight (util/from-local-date-time
                       (tcore/date-time
                         (tcore/year record-date) (tcore/month record-date) (tcore/day record-date)
                         (dec const/midnight-hour) 59 59))
            midnight-working-minutes (if (tcore/before? midnight leave-time)
                                       (tcore/in-minutes (tcore/interval midnight leave-time))
                                       0)]
        (assoc acc user-id (assoc r
                             :prev-day prev-day
                             :working-time minutes
                             :continuous-working-days continuous-working-day
                             :off-day-working-days (if (holiday? record-date) 1 0)
                             :midnight-working-minutes midnight-working-minutes))))))

(defn ranking-indexed
  "ランキングにインデックスを付ける"
  [idx r]
  (assoc r :ranked-at (+ idx 1)))

(defn ranking-cut-off
  "ランキングの上下以外を切り捨てる"
  [user limit records]
  (if (:admin user)
    (do
      records)
    (let [user-id (:id user)
          count (count records)]
      (->>
        records
        (filter
          (fn [r]
            (or
              (<= (:ranked-at r) limit)
              (>= (:ranked-at r) (- count limit -1))
              (= (:user_id r) user-id))))
        (map (fn [r] (if (> (:ranked-at r) (/ count 2))
                       (assoc r :ranked-at (- (:ranked-at r) count 1))
                       r)))))))

(defn calc-deviation
  "偏差値を計算する"
  [keyword records]
  (let [count (count records)
        ; 合計
        total (reduce (fn [acc r] (+ acc (keyword r))) 0 records)
        ; 平均
        average (if (> count 0) (float (/ total count)) 0)
        ; 標準偏差
        standard-deviation (reduce (fn [acc r]
                                     (let [n (- (keyword r) average)]
                                       (+ acc (* n n)))) 0 records)
        standard-deviation (if (and (> count 0) (> standard-deviation 0))
                             (nt/sqrt (/ (float standard-deviation) count))
                             0)]
    ; 偏差値
    (map (fn [r] (let [value (keyword r)]
                   (assoc r
                     :deviation (if (> standard-deviation 0)
                                  (util/round (+ (* (/ (- value average) standard-deviation) 10) 50) 100)
                                  0)
                     :value value))) records)))

(defn sort-and-cut-off-ranking
  "偏差値順に並べて、上位と下位と自分だけのランキングにする"
  [user value records]
  (->>
    records
    (sort (fn [x y] (> (value x) (value y))))
    (map-indexed ranking-indexed)
    (ranking-cut-off user const/ranking-limit)))

;;; ランキングを作成する
(defmulti make-ranking
  (fn [type user-id records]
    (identity type)))
;;; 勤務時間ランキングを作る
(defmethod make-ranking const/ranking-type-working-hours
  [type user records]
  (let [keyword :working-time
        ; 記録の変換なし
        records (->>
                  records)]
    (->>
      records
      (calc-deviation keyword)
      (sort-and-cut-off-ranking user :deviation))))
;;; 時給ランキングを作る
(defmethod make-ranking const/ranking-type-hourly-wage
  [type user records]
  (let [keyword :hourly-wage
        ; 年収が入力されている場合は、年収を12と勤務時間(時)で割って時給(:hourly-wage)を出す
        records (->>
                  records
                  (filter #(not (nil? (:income %))))
                  (map #(assoc % :hourly-wage (/ (/ (:income %) 12.0) (/ (:working-time %) 60)))))]
    (->>
      records
      (calc-deviation keyword)
      (sort-and-cut-off-ranking user :deviation))))
;;; 連続出勤ランキングを作る
(defmethod make-ranking const/ranking-type-continuous-working
  [type user records]
  (let [keyword :continuous-working-days
        ; 連続出勤日数が0日以上の人のみ
        records (->>
                  records
;                  (filter #(> (keyword %) 0))
                  )]
    (->>
      records
      (sort-and-cut-off-ranking user keyword))))
;;; 休日出勤ランキングを作る
(defmethod make-ranking const/ranking-type-off-day-working
  [type user records]
  (let [keyword :off-day-working-days
        ; 休日出勤日数が0日以上の人のみ
        records (->>
                  records
;                  (filter #(> (keyword %) 0))
                  )]
    (->>
      records
      (sort-and-cut-off-ranking user keyword))))
;;; 深夜残業ランキングを作る
(defmethod make-ranking const/ranking-type-midnight-working-minutes
  [type user records]
  (let [keyword :midnight-working-minutes
        ; 記録の変換なし
        records (->>
                  records)]
    (->>
      records
      (calc-deviation keyword)
      (sort-and-cut-off-ranking user :deviation))))

(defn make-ranking-data
  "ランキングデータを作成する"
  [user today]
  ; 1ヶ月前から当日まで
  (let [start-date (tcore/plus
                     (tcore/date-time (tcore/year today) (tcore/month today) (tcore/day today))
                     (tcore/months -1))
        end-date (tcore/plus
                   (tcore/date-time (tcore/year today) (tcore/month today) (tcore/day today))
                   (tcore/days 0))
        records (->>
                  ; 期間の記録を検索
                  (records/select-records-by-period start-date end-date)
                  ; ユーザごとの勤務時間(分)・連続出勤日数・休日出勤日数を計算
                  (reduce calc-summary-by-user {})
                  (vals))]
    {:ranking-data (for [type (map #(:type %) const/ranking-types)]
                     (list type (make-ranking type user records)))}))
