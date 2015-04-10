(ns worker.routes.home
  (:require [worker.const :as const]
            [compojure.core :refer [defroutes GET POST]]
            [noir.session :as session]
            [noir.response :as res]
            [noir.validation :as vali]
            [worker.models.records :as records]
            [worker.lib.auth :as lauth]
            [worker.views.home :as home-view]
            [worker.lib.util :as util]
            [worker.lib.ranking :as ranking]
            [worker.lib.twitter :as twitter]
            [clj-time.core :as tcore]
            [taoensso.timbre :as tim]))

(defn get-today
  "表示する日を取得する"
  ([user-id today-str]
    (let [coming-record (records/select-coming-record user-id)]
      (get-today user-id today-str coming-record)))
  ([user-id today-str coming-record]
    (let [; 表示する日付
          today (if (empty? today-str)
                  ; today-strが空
                  (if (not (empty? coming-record))
                    ; 退社していない日があればその日
                    (util/from-sql (:record_date coming-record))
                    ; 本当の今日
                    (util/today))
                  ; today-strからtoday作成
                  (util/yyyymmdd-to-date today-str))
          today (if (tcore/before? today const/min-record-date)
                  ; 最小の日付より昔は無理
                  const/min-record-date
                  ; 今日より未来は無理
                  (if (tcore/after? today (util/today))
                    (util/today)
                    today))]
      today)))

(defn get-data
  "必要なデータを取得する"
  ([user-id today]
    (let [coming-record (records/select-coming-record user-id)
          near-records (records/select-near-records user-id today)]
      (get-data user-id today coming-record near-records)))
  ([user-id today coming-record near-records]
    (let [prev-record (nth near-records 0)
          today-record (nth near-records 1)
          next-record (nth near-records 2)
          coming-record (if (and (not (empty? coming-record))
                              (= today (util/from-local-date-time (util/from-sql (:record_date coming-record)))))
                          nil coming-record)]
      ; ボタンの状態を決めるローカル関数群
      (letfn [(come-button [data]
                ; 出社ボタン・出社時刻
                (if (empty? coming-record)
                  ; 退社していない日がない
                  (if (or (empty? today-record)
                        (nil? (:come_time today-record)))
                    ; 出社していない
                    (assoc data :come-button :primary, :come-time :enable)
                    ; 出社済
                    (assoc data :come-button :enable, :come-time :enable))
                  ; 退社していない日がある
                  (assoc data :come-button :disable, :come-time :static)))
              (come-time [data]
                ; 出社時刻(時分)
                (if (or (empty? today-record)
                      (nil? (:come_time today-record)))
                  ; 出社していない
                  (let [now (util/now)
                        now-hour (tcore/hour now)
                        now-minute (tcore/minute now)]
                    (assoc data :come-hour now-hour, :come-minute now-minute))
                  ; 出社済
                  (let [come-time (util/to-local-date-time
                                    (util/from-sql (:come_time today-record)))
                        come-hour (tcore/hour come-time)
                        come-minute (tcore/minute come-time)]
                    (assoc data :come-hour come-hour, :come-minute come-minute))))
              (leave-button [data]
                ; 退社ボタン・退社時刻
                (if (empty? coming-record)
                  ; 退社していない日がない
                  (if (or (empty? today-record)
                        (nil? (:come_time today-record)))
                    ; 出社していない
                    (assoc data :leave-button :disable, :leave-time :disable)
                    ; 出社済
                    (if (nil? (:leave_time today-record))
                      ; 退社していない
                      (assoc data :leave-button :primary, :leave-time :enable)
                      ; 退社済
                      (assoc data :leave-button :enable, :leave-time :enable)))
                  ; 退社していない日がある
                  (assoc data :leave-button :disable, :leave-time :static)))
              (leave-time [data]
                ; 退社時刻(時分)
                (if (or (empty? today-record)
                      (nil? (:leave_time today-record)))
                  ; 退社していない
                  (let [now (util/now)
                        now-hour (tcore/hour now)
                        now-minute (tcore/minute now)]
                    (assoc data :leave-hour now-hour, :leave-minute now-minute))
                  ; 退社済
                  (let [leave-time (util/to-local-date-time
                                     (util/from-sql (:leave_time today-record)))
                        hours (tcore/in-hours
                                (tcore/interval today leave-time))
                        leave-hour hours
                        leave-minute (tcore/minute leave-time)]
                    (assoc data :leave-hour leave-hour, :leave-minute leave-minute))))]
        (->
          {:coming-record coming-record,
           :today today,
           :prev-record prev-record,
           :today-record today-record,
           :next-record next-record,
           ; 出社削除ボタン
           :come-remove-button
           (if (empty? coming-record)
             ; 退社していない日がない
             (if (or (empty? today-record)
                   (nil? (:come_time today-record)))
               ; 出社していない
               :disable
               ; 出社済
               :enable)
             ; 退社していない日がある
             :disable)
           ; 退社削除ボタン
           :leave-remove-button
           (if (empty? coming-record)
             ; 退社していない日がない
             (if (or (empty? today-record)
                   (nil? (:leave_time today-record)))
               ; 退社していない
               :disable
               ; 退社済
               :enable)
             ; 退社していない日がある
             :disable)
           ; 前へボタン
           :prev-button
           (if (or (= today const/min-record-date)
                 (tcore/before? today const/min-record-date))
             :disable
             :enable)
           ; 次へボタン
           :next-button
           (if (or (= today (util/today)) (tcore/after? today (util/today)))
             :disable
             :enable)
           }
          (come-button)
          (come-time)
          (leave-button)
          (leave-time))))))

(defn handle-home
  "メイン画面表示(GET)"
  [today-str]
  ; 認証チェック
  (if-let [user (lauth/login?)]
    ; ログイン済
    (let [user-id (:id user)
          today (get-today user-id today-str)
          data (get-data user-id today)
          data (merge data (ranking/make-ranking-data user (util/today)))
          start-date (tcore/plus
                       (tcore/date-time (tcore/year today) (tcore/month today) (tcore/day today))
                       (tcore/months -2))
          end-date (tcore/plus
                     (tcore/date-time (tcore/year today) (tcore/month today) (tcore/day today))
                     (tcore/days 0))
          data (assoc data :daily-records
                 (map #(assoc % :working-time
                         (ranking/calc-working-time
                           (util/from-sql (:come_time %))
                           (util/from-sql (:leave_time %)))) (records/select-records-by-period user-id start-date end-date)))
          coming-record (:coming-record data)]
      (if (and (not (empty? coming-record))
            (not= today (util/from-local-date-time (util/from-sql (coming-record :record_date)))))
        (let [come-date (util/from-sql (coming-record :record_date))]
          (session/flash-put! const/flash-message-success (str (util/date-to-str come-date) "退社しないと他の日の入力はできません"))))
      (home-view/input-home user data))
    ; 未ログイン
    (home-view/show nil)))

(defn valid-come?
  "出社バリデーション"
  [user-id {:keys [date come_time_hour come_time_minute] :as params}]
  (let [date-str date
        date (util/yyyymmdd-to-date date-str)]
    (vali/rule (vali/valid-number? come_time_hour)
      [:come_time_hour "正しい数字を入力してください"])
    (vali/rule (vali/valid-number? come_time_minute)
      [:come_time_minute "正しい数字を入力してください"])
    (if (and (vali/valid-number? come_time_hour) (vali/valid-number? come_time_minute))
      (let [come-time-hour (Long/parseLong come_time_hour)
            come-time-minute (Long/parseLong come_time_minute)]
        ; 時刻として有効か(00:00〜23:59)
        (vali/rule (and (>= come-time-hour 0) (> 24 come-time-hour))
          [:come_time_hour "0～23時の間で入力してください"])
        (vali/rule (and (>= come-time-minute 0) (> 60 come-time-minute))
          [:come_time_minute "0～59分の間で入力してください"])
        (if (and
              (>= come-time-hour 0) (> 24 come-time-hour)
              (>= come-time-minute 0) (> 60 come-time-minute))
          ; 前のレコードの退社時刻と被ってないか
          (let [come-time (util/from-local-date-time
                            (tcore/date-time (tcore/year date) (tcore/month date) (tcore/day date)
                              (Long/parseLong come_time_hour) (Long/parseLong come_time_minute)))
                records (records/select-near-records user-id date)
                prev-record (nth records 0)
                today-record (nth records 1)
                next-record (nth records 2)]
            (if (and prev-record (:leave_time prev-record))
              (vali/rule (tcore/after? come-time (util/from-sql (:leave_time prev-record)))
                [:come_time "前の退社時刻より後の時刻を入力してください"]))))))
    (not (vali/errors? :come_time :come_time_hour :come_time_minute))))

(defn valid-leave?
  "退社バリデーション"
  [user-id {:keys [date come_time_hour come_time_minute leave_time_hour leave_time_minute] :as params}]
  (let [date-str date
        date (util/yyyymmdd-to-date date-str)]
    ; 数値かどうか
    (vali/rule (vali/valid-number? leave_time_hour)
      [:leave_time_hour "正しい数字を入力してください"])
    (vali/rule (vali/valid-number? leave_time_minute)
      [:leave_time_minute "正しい数字を入力してください"])
    (if (and (vali/valid-number? leave_time_hour) (vali/valid-number? leave_time_minute))
      (let [leave-time-hour (Long/parseLong leave_time_hour)
            leave-time-minute (Long/parseLong leave_time_minute)]
        ; 時刻として有効か(00:00〜71:59)
        (vali/rule (and (>= leave-time-hour 0) (> 72 leave-time-hour))
          [:leave_time_hour "0～71時の間で入力してください"])
        (vali/rule (and (>= leave-time-minute 0) (> 60 leave-time-minute))
          [:leave_time_minute "0～59分の間で入力してください"])
        ; 出社時刻
        (valid-come? user-id params)
        (if (and
              (>= leave-time-hour 0) (> 24 leave-time-hour)
              (>= leave-time-minute 0) (> 60 leave-time-minute))
          ; 出社時刻より後か
          (let [come-time (util/from-local-date-time
                            (tcore/date-time (tcore/year date) (tcore/month date) (tcore/day date)
                              (Long/parseLong come_time_hour) (Long/parseLong come_time_minute)))
                days (quot leave-time-hour 24)
                hours (mod leave-time-hour 24)
                leave-time (util/from-local-date-time
                             (tcore/plus
                               (tcore/date-time (tcore/year date) (tcore/month date) (tcore/day date)
                                 hours leave-time-minute) (tcore/days days)))]
            (vali/rule (tcore/before? come-time leave-time)
              [:leave_time "出社時刻よりも後の時刻を入力してください"])
            ; 後のレコードの出社時刻と被ってないか
            (let [records (records/select-near-records user-id date)
                  prev-record (nth records 0)
                  today-record (nth records 1)
                  next-record (nth records 2)]
              (if (and next-record (:come_time next-record))
                (vali/rule (tcore/before? leave-time (util/from-sql (:come_time next-record)))
                  [:leave_time "後の出社時刻より前の時刻を入力してください"])))))))
    (not (vali/errors? :leave_time :leave_time_hour :leave_time_minute))))

(defn handle-come
  "出社"
  [{:keys [date come_time_hour come_time_minute] :as params}]
  (let [date-str date
        date (util/yyyymmdd-to-date date-str)]
    (if-let [user (lauth/login?)]
      (let [user-id (:id user)]
        ; バリデーション
        (if (valid-come? user-id params)
          (let [come-time (util/from-local-date-time
                            (tcore/date-time (tcore/year date) (tcore/month date) (tcore/day date)
                              (Long/parseLong come_time_hour) (Long/parseLong come_time_minute)))]
            (records/upsert-record-come user-id date come-time)
            (session/flash-put! const/flash-message-success "出社しました"))))
      ; 未ログイン
      (res/redirect "/"))
    (if (vali/errors?)
      (handle-home date-str)
      (if date-str (res/redirect (str "/?date=" date-str)) (res/redirect "/")))))

(defn handle-leave
  "退社"
  [{:keys [date come_time_hour come_time_minute leave_time_hour leave_time_minute] :as params}]
  (let [date-str date
        date (util/yyyymmdd-to-date date-str)]
    (if-let [user (lauth/login?)]
      (let [user-id (:id user)]
        ; バリデーション
        (if (valid-leave? user-id params)
          (let [leave-time-hour (Long/parseLong leave_time_hour)
                leave-time-minute (Long/parseLong leave_time_minute)
                days (quot leave-time-hour 24)
                hours (mod leave-time-hour 24)
                leave-time (util/from-local-date-time
                             (tcore/plus
                               (tcore/date-time (tcore/year date) (tcore/month date) (tcore/day date)
                                 hours leave-time-minute) (tcore/days days)))]
            (records/upsert-record-leave user-id date leave-time)
            (session/flash-put! const/flash-message-success "退社しました"))))
      ; 未ログイン
      (res/redirect "/"))
    (if (vali/errors?)
      (handle-home date-str)
      (if date-str (res/redirect (str "/?date=" date-str)) (res/redirect "/")))))

(defn handle-remove-come
  "出社時間削除"
  [date-str]
  (let [date (util/yyyymmdd-to-date date-str)]
    (if-let [user (lauth/login?)]
      (let [user-id (:id user)]
        ; 何かチェックする?
        (records/delete-record-by-date user-id date)
        (session/flash-put! const/flash-message-success "出社時間を削除しました"))
      ; 未ログイン
      (res/redirect "/"))
    (if (vali/errors?)
      (handle-home date-str)
      (if date-str (res/redirect (str "/?date=" date-str)) (res/redirect "/")))))

(defn handle-remove-leave
  "退社時間削除"
  [date-str]
  (let [date (util/yyyymmdd-to-date date-str)]
    (if-let [user (lauth/login?)]
      (let [user-id (:id user)
            record (records/select-record-by-date user-id date)
            come-time (:come_time record)
            note (:note record)]
        ; 何かチェックする?
        (records/upsert-record-leave user-id date nil)
        (session/flash-put! const/flash-message-success "退社時間を削除しました"))
      ; 未ログイン
      (res/redirect "/"))
    (if (vali/errors?)
      (handle-home date-str)
      (if date-str (res/redirect (str "/?date=" date-str)) (res/redirect "/")))))

(defn handle-tweet-ranking
  "ランキングの偏差値をTwitterにつぶやく"
  [date type rank value]
  (if-let [user (lauth/login?)]
    (if-let [request-token
             (condp #(= %1 (Long/parseLong %2)) type
               ; TODO: const使う
               ; 勤務時間
               const/ranking-type-working-hours (twitter/tweet user
                                                    (str (:nickname user) "さんの勤務時間 偏差値は " value " です。現在、" rank "位です。 http://www.syatiku.net/ #社畜net")
                                                    "/twitter_callback2")
               ; 時給
               const/ranking-type-hourly-wage (twitter/tweet user
                                                  (str (:nickname user) "さんの時給 偏差値は " value " です。現在、" rank "位です。 http://www.syatiku.net/ #社畜net")
                                                  "/twitter_callback2")
               ; 連続出勤ランキング
               const/ranking-type-continuous-working (twitter/tweet user
                                                       (str (:nickname user) "さんは連続 " value "日出勤中です。現在、" rank "位です。 http://www.syatiku.net/ #社畜net")
                                                       "/twitter_callback2")
               ; 休日出勤ランキング
               const/ranking-type-off-day-working (twitter/tweet user
                                                    (str (:nickname user) "さんは休日 " value "日出勤(過去30日)しています。現在、" rank "位です。 http://www.syatiku.net/ #社畜net")
                                                    "/twitter_callback2")
               ; 深夜残業ランキング
               const/ranking-type-midnight-working-minutes (twitter/tweet user
                                                    (str (:nickname user) "さんの深夜残業 偏差値は  " value "です。現在、" rank "位です。 http://www.syatiku.net/ #社畜net")
                                                    "/twitter_callback2"))]
      ; Twitterへリダイレクト
      (res/redirect (.getAuthenticationURL request-token))
      ; Twitterへつぶやき完了
      (do
        (session/flash-put! const/flash-message-success "Twitterにつぶやきました")
        (res/redirect "/")))
    ; 未ログイン
    (res/redirect "/")))

(defn handle-twitter-callback2
  "Twitterコールバック"
  [{:keys [oauth_token oauth_verifier] :as params}]
  (if (twitter/twitter-callback oauth_token oauth_verifier twitter/handle-tweet-callback)
    ; Twitterへつぶやき完了
    (do
      (session/flash-put! const/flash-message-success "Twitterにつぶやきました")
      (res/redirect "/"))
    ; ログイン or 拒否
    (res/redirect "/")))

(defn handle-work-table
  "勤務表ダウンロード"
  [from to]
  ; TODO: from, toを実装する & リファクタリング
  (if-let [user (lauth/login?)]
    (let [user-id (:id user)
          today (util/today)
          data (get-data user-id today)
          data (merge data (ranking/make-ranking-data user (util/today)))
          start-date (tcore/plus
                       (tcore/date-time (tcore/year today) (tcore/month today) (tcore/day today))
                       (tcore/months -2))
          end-date (tcore/plus
                     (tcore/date-time (tcore/year today) (tcore/month today) (tcore/day today))
                     (tcore/days 0))
          data (assoc data :daily-records
                 (map #(assoc % :working-time
                         (ranking/calc-working-time
                           (util/from-sql (:come_time %))
                           (util/from-sql (:leave_time %)))) (records/select-records-by-period user-id start-date end-date)))
          coming-record (:coming-record data)
          records (:daily-records data)]
      (res/content-type "text/csv;charset=utf-8"
        (for [r (reverse records)]
          (let [record-date (util/date-to-format-str (util/from-sql (:record_date r)) "yyyy/MM/dd")
                come-time (util/date-to-format-str (util/from-sql (:come_time r)) "HH:mm")
                leave-time (util/date-to-format-str (util/from-sql (:leave_time r)) "HH:mm")
                working-time (str (quot (:working-time r) 60) ":" (format "%02d" (mod (:working-time r) 60)))]
            (str record-date "," come-time "," leave-time "," working-time "\n")))))
    ; 未ログイン
    (res/redirect "/")))

(defroutes home-routes
  "ルーティング定義"
  (GET "/" {params :params} [] (handle-home (params :date)))
  (POST "/come" {params :params} (handle-come params))
  (POST "/leave" {params :params} (handle-leave params))
  (GET "/remove-come/" {params :params} [] (handle-remove-come (params :date)))
  (GET "/remove-leave/" {params :params} [] (handle-remove-leave (params :date)))
  (GET "/tweet_ranking" {params :params}
    (let [ranking-type (params :type)
          ranking-data (nth const/ranking-types (dec (Long/parseLong ranking-type)))]
      (handle-tweet-ranking (params :date) (params :type) (params :rank) (params :value))))
  (GET "/twitter_callback2" {params :params}
    (handle-twitter-callback2 params))
  (GET "/work-table.csv" {params :params}
    (handle-work-table (params :from) (params :to))))
