(ns worker.lib.logger
  (:require [com.postspectacular.rotor :as rotor]
            [taoensso.timbre :as tim]))

(defn init
  "ロガー初期化"
  []
  (do
    ; ロガーの時刻のフォーマット
    (tim/set-config! [:timestamp-pattern] "yyyy-MM-dd HH:mm:ss")
    ; ロガーの出力設定
    (tim/set-config!
      [:appenders :rotor]
      {:doc "Writes to to (:path (:rotor :shared-appender-config)) file
         and creates optional backups."
       :min-level :info
       :enabled? true
       :async? false ; should be always false for rotor
       :max-message-per-msecs nil
       :fn rotor/append})
    (tim/set-config!
      [:shared-appender-config :rotor]
      {:path "logs/app.log" :max-size (* 512 1024) :backlog 5})))

;  (def levels-ordered [:trace :debug :info :warn :error :fatal :report])

;  (info "This will print") => nil
;  %> 2012-May-28 17:26:11:444 +0700 localhost INFO [my-app] - This will print
;
;  (spy :info (* 5 4 3 2 1)) => 120
;  %> 2012-May-28 17:26:14:138 +0700 localhost INFO [my-app] - (* 5 4 3 2 1) 120
;
;  (trace "This won't print due to insufficient logging level") => nil
