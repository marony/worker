(ns worker.views.layout
  (:require [worker.const :as const]
            [hiccup.page :as page]
            [hiccup.element :as element]
            [noir.session :as session])
  (:use [carica.core]))

(defn common
  "標準レイアウト定義"
  [title & body]
  (page/html5
    [:head
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
     [:link {:rel "SHORTCUT ICON" :href "/favicon.ico"}]
     (page/include-js "//ajax.googleapis.com/ajax/libs/jquery/1.11.1/jquery.min.js")
     (page/include-js "//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/js/bootstrap.min.js")
     (page/include-css "/css/scheme.less")
     (page/include-css "//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css")
     (page/include-css "//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap-theme.min.css")
     (page/include-css "/css/1pxdeep.less")
     (page/include-css "/css/social-buttons.css")
     (page/include-css "/css/font-awesome.min.css")
     [:title title]]
    [:body
     [:div.container
      [:header
       [:div.row
        [:div.col-sm-6
         (element/link-to "/"
           (element/image (config :logo-path) "Logo"))]]]
      (let [success (session/flash-get const/flash-message-success)
            info (session/flash-get const/flash-message-info)
            warning (session/flash-get const/flash-message-warning)
            danger (session/flash-get const/flash-message-danger)]
        (list
          (if success
            [:div.alert.alert-success success])
          (if info
            [:div.alert.alert-info info])
          (if warning
            [:div.alert.alert-warning warning])
          (if danger
            [:div.alert.alert-danger danger])))
      body
      [:footer
       [:div.row
        [:div.col-sm-2.col-sm-offset-10
         [:p "&copy; marony 2014"]]]]]]))
