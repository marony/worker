(ns worker.views.home
  (:require [hiccup.form :as form]
            [hiccup.page :as page]
            [hiccup.element :as element]
            [noir.validation :as vali]
            [worker.views.layout :as layout]
            [worker.views.parts :as parts]
            [worker.const :as const]
            [worker.lib.util :as util]
            [worker.lib.ranking :as ranking]
            [clj-time.core :as tcore])
  (:use [carica.core]))

(defn error-message
  "エラーメッセージ"
  [[first-error]]
  [:div.label.label-danger first-error])

(defn show
  "ホーム画面(未ログイン)定義"
  [user]
  (layout/common
    (str "Home - " const/site-name)
    [:div.row
     [:div.col-sm-3.col-sm-push-9
      (parts/login-form user)]
     [:div.col-sm-9.col-sm-pull-3
      [:div.jumbotron
       [:h3.text-muted const/site-name]
       [:div.well
        (str "社畜（しゃちく）とは、主に日本で、勤めている会社に飼い慣らされてしまい自分の意思と良心を放棄し奴隷（家畜）と化したサラリーマンの状態を揶揄したものである。"
          "「会社+家畜」から来た造語で、「会社人間」や「企業戦士」などよりも、皮肉が強く込められている言葉である。"
          " 英語圏では同様の概念として「wage slave」が存在する。(Wikipedia)")]
       (element/link-to { :class "btn btn-primary btn-lg" } "/register" "ユーザー登録")]]]))

(defn one-ranking
  "ひとつのランキング"
  [user data today today-str type title column-name value description]
  (let [ranking_ (nth (:ranking-data data) (dec type) [])
        ranking (nth ranking_ 1 [])
        user-rank (first (filter #(= (:id user) (:user_id %)) ranking))
        rank-class (str "ranking-" type)]
    (list
      [:div.col-sm-6
       (list
         [:div.panel.panel-info
          [:div.panel-heading
           [:a {:data-toggle "collapse" :data-target (str "#" rank-class) :href (str "#" rank-class)}
            [:span.glyphicon.glyphicon-chevron-down {:id (str rank-class "-glyph")} title]]]
          [:div.panel-collapse.collapse {:id rank-class}
           [:div.panel-body
            [:div
             description]
            (if (not (empty? user-rank))
              (element/link-to (str "/tweet_ranking?date=" today-str
                                 "&type=" type "&rank=" (:ranked-at user-rank)
                                 "&value=" (value user-rank))
                [:button.btn.btn-twitter [:i.fa.fa-twitter] " | 成績をつぶやく"]))]
           [:table.table.table-striped
            [:thead
             [:tr
              [:th "順位"]
              [:th "ニックネーム"]
              [:th column-name]
              (if (:admin user)
                (list [:th "値"]))]]
            [:tbody
             (list
               (for [r ranking]
                 [:tr (if (= (:id user) (:user_id r)) {:class "success"} {})
                  [:td (:ranked-at r)]
                  [:td (:nickname r)]
                  [:td (value r)]
                  (if (:admin user) (list [:td (:value r)]))]))]]]])]
      (element/javascript-tag
        (str
          ; 開き終わったら
          "$(document).on('show.bs.collapse', '" (str "#" rank-class) "', function () {"
          "$('" (str "#" rank-class "-glyph") "').removeClass('glyphicon-chevron-down').addClass('glyphicon-chevron-up');"
          "});"
          ; 閉じ終わったら
          "$(document).on('hide.bs.collapse', '" (str "#" rank-class) "', function () {"
          "$('" (str "#" rank-class "-glyph") "').removeClass('glyphicon-chevron-up').addClass('glyphicon-chevron-down');"
          "});")))))

(defn ranking
  "ランキング"
  [user data]
  (let [today (:today data)
        today-str (util/date-to-yyyymmdd today)]
    (for [type const/ranking-types]
      (one-ranking user data today today-str
        (:type type) (:title type) (:name type)
        (:value type) (:description type)))))

(defn work-table
  "過去2ヶ月分の勤務表"
  [user data]
  (list
    [:div.col-sm-12
     (let [records (:daily-records data)
           from ""
           to ""]
       (list
         [:div.panel.panel-info
          [:div.panel-heading
           [:a {:data-toggle "collapse" :data-target "#work-table" :href "#work-table"}
            [:span.glyphicon.glyphicon-chevron-down {:id "work-table-glyph"} "勤務表"]]]
          [:div.panel-collapse.collapse {:id "work-table"}
           [:div.panel-body
            [:span "過去2ヶ月分の勤務表"]
            [:span (element/link-to (str "/work-table.csv?from=" from "&to=" to) [:span.glyphicon.glyphicon-download-alt])]]
           [:table.table.table-striped
            [:thead
             [:tr
              [:th "日付"]
              [:th "出社"]
              [:th "退社"]
              [:th "時間"]]]
            [:tbody
             (list
               (for [r (reverse records)]
                 (let [record-date (util/from-sql (:record_date r))
                       record-date (util/date-to-format-str record-date (str "MM-dd" (util/date-to-week-str record-date)))
                       come-time (util/date-to-format-str (util/from-sql (:come_time r)) "HH:mm")
                       leave-time (util/date-to-format-str (util/from-sql (:leave_time r)) "HH:mm")
                       working-time (str (quot (:working-time r) 60) ":" (format "%02d" (mod (:working-time r) 60)))]
                   [:tr
                    [:td record-date]
                    [:td come-time]
                    [:td leave-time]
                    [:td working-time]])))]]]]))]
    (element/javascript-tag
      (str
        "jQuery(function($) {"
        ; 開き終わったら
        "$(document).on('show.bs.collapse', '#work-table', function () {"
        "$('#work-table-glyph').removeClass('glyphicon-chevron-down').addClass('glyphicon-chevron-up');"
        "});"
        ; 閉じ終わったら
        "$(document).on('hide.bs.collapse', '#work-table', function () {"
        "$('#work-table-glyph').removeClass('glyphicon-chevron-up').addClass('glyphicon-chevron-down');"
        "});"
        "});"))))

(defn- panel-header
  "入力パネルのヘッダ"
  [data]
  (let [today (:today data)]
    (list
      [:div.panel-heading
       [:div.row
        [:div.panel-title
         [:span.col-sm-2.text-muted "出勤簿"]
         [:div.col-sm-4.col-sm-offset-2
          (util/date-to-str today true)]
         [:div.col-sm-2.col-sm-offset-2
          (element/link-to {} "/" "本日")]]]])))

(defn- come-form
  "出社時間フォーム"
  [user data]
  (let [today (:today data)
        today-str (util/date-to-yyyymmdd today)
        yesterday-str (util/date-to-yyyymmdd (tcore/plus today (tcore/days -1)))
        tomorrow-str (util/date-to-yyyymmdd (tcore/plus today (tcore/days 1)))]
    (list
      (form/form-to { :class "form-horizontal" } [:post "/come"]
        (form/hidden-field "date" today-str)
        [:form-group
         [:div (if (vali/errors? :come_time) {:class "col-sm-3 has-error"} {:class "col-sm-3"})
          (case (:come-button data)
            :primary (form/submit-button { :class "form-control btn-primary" } "出社")
            :enable (form/submit-button { :class "form-control" } "出社")
            :disable (form/submit-button { :class "form-control" :disabled "disabled" } "出社"))
          (vali/on-error :come_time error-message)]
         [:div.col-sm-4
          [:div (if (or (vali/errors? :come_time) (vali/errors? :come_time_hour))
                  {:class "input-group has-error"}
                  {:class "input-group"})
           (case (:come-time data)
             :enable (list
                       (form/drop-down {:placeholder "出社時刻(時)", :class "form-control"}
                         "come_time_hour" (range 0 24) (:come-hour data))
                       [:span.input-group-addon "時"])
             :static (list
                       (form/drop-down {:placeholder "出社時刻(時)", :class "form-control", :disabled "disabled"}
                         "come_time_hour" (range 0 24) (:come-hour data))
                       [:span.input-group-addon "時"])
             :disable (form/label {:class "control-label"} "come_time_hour" ""))]
          (vali/on-error :come_time_hour error-message)]
         [:div.col-sm-4
          [:div (if (or (vali/errors? :come_time) (vali/errors? :come_time_minute))
                  {:class "input-group has-error"}
                  {:class "input-group"})
           (case (:come-time data)
             :enable (list
                       (form/drop-down {:placeholder "出社時刻(分)", :class "form-control"}
                         "come_time_minute" (range 0 60) (:come-minute data))
                       [:span.input-group-addon "分"])
             :static (list
                       (form/drop-down {:placeholder "出社時刻(分)", :class "form-control", :disabled "disabled"}
                         "come_time_minute" (range 0 60) (:come-minute data))
                       [:span.input-group-addon "分"])
             :disable (form/label {:class "control-label"} "come_time_minute" ""))]
          (vali/on-error :come_time_minute error-message)]
         [:div.col-sm-1
          (case (:come-remove-button data)
            :enable (element/link-to (str "/remove-come/?date=" today-str) [:span.glyphicon.glyphicon-remove])
            :disable nil)]]))))

(defn- leave-form
  "退社時間フォーム"
  [user data]
  (let [today (:today data)
        today-str (util/date-to-yyyymmdd today)
        yesterday-str (util/date-to-yyyymmdd (tcore/plus today (tcore/days -1)))
        tomorrow-str (util/date-to-yyyymmdd (tcore/plus today (tcore/days 1)))]
    (list
      (form/form-to { :class "form-horizontal" } [:post "/leave"]
        (form/hidden-field "date" today-str)
        (form/hidden-field "come_time_hour" (:come-hour data))
        (form/hidden-field "come_time_minute" (:come-minute data))
        [:form-group
         [:div (if (vali/errors? :leave_time) {:class "col-sm-3 has-error"} {:class "col-sm-3"})
          (case (:leave-button data)
            :primary (form/submit-button { :class "form-control btn-primary" } "退社")
            :enable (form/submit-button { :class "form-control" } "退社")
            :disable (form/submit-button { :class "form-control" :disabled "disabled" } "退社"))
          (vali/on-error :leave_time error-message)]
         [:div.col-sm-4
          [:div (if (or (vali/errors? :leave_time) (vali/errors? :leave_time_hour))
                  {:class "input-group has-error"} {:class "input-group"})
           (case (:leave-time data)
             :enable (list
                       (form/drop-down {:placeholder "退社時刻(時)", :class "form-control"}
                         "leave_time_hour" (range (:come-hour data) 72) (:leave-hour data))
                       [:span.input-group-addon "時"])
             :static (list
                       (form/drop-down {:placeholder "退社時刻(時)", :class "form-control", :disabled "disabled"}
                         "leave_time_hour" (range (:come-hour data) 72) (:leave-hour data))
                       [:span.input-group-addon "時"])
             :disable (form/label {:class "control-label"} "leave_time_hour" ""))]
          (vali/on-error :leave_time_hour error-message)]
         [:div.col-sm-4
          [:div (if (or (vali/errors? :leave_time) (vali/errors? :leave_time_minute)) {:class "input-group has-error"} {:class "input-group"})
           (case (:leave-time data)
             :enable (list
                       (form/drop-down {:placeholder "退社時刻(分)", :class "form-control"}
                         "leave_time_minute" (range 0 60) (:leave-minute data))
                       [:span.input-group-addon "分"])
             :static (list
                       (form/drop-down {:placeholder "退社時刻(分)", :class "form-control", :disabled "disabled"}
                         "leave_time_minute" (range 0 60) (:leave-minute data))
                       [:span.input-group-addon "分"])
             :disable (form/label {:class "control-label"} "leave_time_minute" ""))]
          (vali/on-error :leave_time_minute error-message)]
         [:div.col-sm-1
          (case (:leave-remove-button data)
            :enable (element/link-to (str "/remove-leave/?date=" today-str) [:span.glyphicon.glyphicon-remove])
            :disable nil)]]))))

(defn- panel-body
  "入力パネルの本体"
  [user data]
  (let [today (:today data)]
    (list
      [:panel-body.container
       [:div.panel.panel-default
        [:div.panel-body
         (come-form user data)]]
       [:div.panel.panel-default
        [:div.panel-body
         (leave-form user data)]]])))

(defn- panel-footer
  "入力パネルのフッタ"
  [data]
  (let [today (:today data)
        today-str (util/date-to-yyyymmdd today)
        yesterday-str (util/date-to-yyyymmdd (tcore/plus today (tcore/days -1)))
        tomorrow-str (util/date-to-yyyymmdd (tcore/plus today (tcore/days 1)))]
    (list
      [:ul.pager
       [:li.previous
        (if (= (:prev-button data) :enable)
          (element/link-to (str "/?date=" yesterday-str) [:span.glyphicon.glyphicon-backward])
          nil)]
       [:li
        (element/link-to (str "/?date=" today-str) [:span.glyphicon.glyphicon-refresh])]
       [:li.next
        (if (= (:next-button data) :enable)
          (element/link-to (str "/?date=" tomorrow-str) [:span.glyphicon.glyphicon-forward])
          nil)]])))

(defn input-home
  "ホーム画面(ログイン済入力画面)定義"
  [user data]
  (layout/common
    (str "Home - " const/site-name)
    (list
      [:div.row
       [:div.col-sm-3.col-sm-push-9
        (parts/login-form user data)]
       [:div.col-sm-9.col-sm-pull-3
        [:div.panel.panel-info
         (panel-header data)
         (panel-body user data)
         [:div.panel-footer
          (panel-footer data)]]]]
      (ranking user data)
      (work-table user data))))
