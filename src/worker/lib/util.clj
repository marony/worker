(ns worker.lib.util
  (:require [clojure.data.codec.base64 :as base64]
            [clojure.string :as str]
            [clj-time.core :as tcore]
            [clj-time.format :as tf]
            [clj-time.local :as tlocal]))
(import 'org.joda.time.LocalDate)
(import 'org.joda.time.LocalTime)

(defn to-local-date-time
  "UTCを日本時間に変換する"
  [datetime]
  (if datetime
    (tcore/to-time-zone datetime (tcore/time-zone-for-id "Asia/Tokyo"))
    nil))

(defn from-local-date-time
  "日本時間をUTCに変換する"
  [datetime]
  (if datetime
    (tcore/from-time-zone datetime (tcore/time-zone-for-id "Asia/Tokyo"))
    nil))

(defn today
  "今日を返す"
  []
  (clj-time.core/from-time-zone
    (. (LocalDate.) toDateTime (LocalTime. 0 0 0))
    (clj-time.core/time-zone-for-id "Asia/Tokyo")))

(defn now
  "今を返す"
  []
  (to-local-date-time (tcore/now)))

(defmulti from-sql class)
(defmethod from-sql java.sql.Date
  [date]
  (if date
    (to-local-date-time (clj-time.coerce/from-sql-date date))
    nil))
(defmethod from-sql java.sql.Timestamp
  [datetime]
  (if datetime
    (to-local-date-time (clj-time.coerce/from-sql-time datetime))
    nil))
(defmethod from-sql :default
  [datetime]
  (println "INVALID:" (class datetime))
  datetime)

(defmulti to-sql class)
(defmethod to-sql org.joda.time.DateTime
  [datetime]
  (if datetime
    (clj-time.coerce/to-sql-time datetime)
    nil))
(defmethod to-sql java.sql.Date
  [date]
  date)
(defmethod to-sql java.sql.Timestamp
  [datetime]
  datetime)

(defn round
  "四捨五入する"
  [x scale]
  (-> x (* scale) Math/round (/ scale) (float)))

(defn string-to-long
  "パラメータがnilでなければ数値に変換し、nilならばnilを返す"
  [s]
  (if (or (nil? s) (not= (class s) java.lang.String))
    nil
    (read-string s)))

(defn master-to-options
  "マスターのリストからselect-options用のリストを返す"
  [list]
  (map (fn [v] [(:name v) (:id v)]) list))

(defn string-to-base64-string
  "base64エンコードする"
  [original]
  (String. (base64/encode (.getBytes original)) "UTF-8"))

(defn valid-date?
  "正しい日付か？"
  [year month day]
  (try
    (tcore/date-time (Long/parseLong year) (Long/parseLong month) (Long/parseLong day))
    true
    (catch Exception _
      false)))

(defn date-time
  "文字列の年月日を日付に変換"
  [year month day]
  (try
    (to-local-date-time (tcore/date-time (Long/parseLong year) (Long/parseLong month) (Long/parseLong day)))
    (catch Exception _
      nil)))

(defn date-to-week-str
  "日付から曜日文字列を取得"
  [date]
  (condp #(= (tcore/day-of-week %2) %1) date
    1 "(月)"
    2 "(火)"
    3 "(水)"
    4 "(木)"
    5 "(金)"
    6 "(土)"
    7 "(日)"))

(defn date-to-str
  "日付を文字列に変換する"
  [date & dow]
  (let [ret (tf/unparse (tf/formatter "yyyy年MM月dd日" (tcore/time-zone-for-id "Asia/Tokyo")) date)]
    (if dow
      (str ret (date-to-week-str date))
      ret)))

(defn date-to-format-str
  "日付を指定したフォーマットの文字列に変換する"
  [date format]
  (tf/unparse (tf/formatter format (tcore/time-zone-for-id "Asia/Tokyo")) date))

(defn yyyymmdd-to-date
  "yyyyMMdd形式の数値を日付に変換する"
  [yyyymmdd]
  (let [formatter (tf/formatter "yyyyMMdd" (tcore/time-zone-for-id "Asia/Tokyo"))]
    (to-local-date-time (tf/parse formatter (str yyyymmdd)))))

(defn date-to-yyyymmdd
  "日付をyyyyMMddに変換する"
  [date]
  (tf/unparse (tf/formatter "yyyyMMdd" (tcore/time-zone-for-id "Asia/Tokyo")) date))
