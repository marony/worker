(ns worker.models.schema
  (:require [worker.const :as const]
            [worker.lib.util :as util]
            [worker.lib.auth :as lauth]
            [clojure.java.jdbc.deprecated :as sql])
  (:use [carica.core]))

; DROP DATABASE worker;
; CREATE DATABASE worker OWNER worker;
; insert into users values(0, 'a@a.com', 'password', 'hash', 'nickname', 0, false, now(), now());

(defn create-trigger
  "トリガ作成用DLL文字列作成"
  [table-name]
  (sql/do-commands
    (str "CREATE OR REPLACE FUNCTION insert_" table-name "_history() RETURNS trigger AS $" table-name "_history$\n"
       "BEGIN\n"
       "IF (TG_OP = 'DELETE') THEN\n"
       "INSERT INTO " table-name "_history SELECT OLD.*, TRUE;\n"
       "ELSE\n"
       "INSERT INTO " table-name "_history SELECT NEW.*, FALSE;\n"
       "END IF;\n"
       "RETURN NULL;\n"
       "END;\n"
       "$" table-name "_history$ LANGUAGE PLPGSQL;"))
  (sql/do-commands
    (str "CREATE TRIGGER insert_" table-name "_history AFTER INSERT OR UPDATE OR DELETE ON " table-name
      " FOR EACH ROW EXECUTE PROCEDURE insert_" table-name "_history()")))

(defn drop-table
  [table-name]
  (sql/with-connection (config :db)
    (sql/do-commands
      (str "DROP TRIGGER insert_" table-name "_history ON " table-name))
    (sql/do-commands
      (str "DROP FUNCTION insert_" table-name "_history()"))
    (sql/do-commands
      (str "DROP TABLE IF EXISTS " table-name))
    (sql/do-commands
      (str "DROP TABLE IF EXISTS " table-name "_history"))))

(defn create-users-table
  "ユーザテーブル作成"
  []
  (sql/with-connection (config :db)
    (sql/create-table
      :users
      [:id "serial PRIMARY KEY"]
      [:email "varchar(255)"]
      [:crypted_password "varchar(255)"]
      [:provider "varchar(32)"]
      [:provider_user_id "bigint"]
      [:provider_access_token "varchar(255)"]
      [:provider_access_token_secret "varchar(255)"]
      [:hash "varchar(255) NOT NULL"]
      [:nickname "varchar(255) NOT NULL"]
      [:status "smallint NOT NULL"]
      [:admin "boolean NOT NULL"]
      [:sakura "boolean NOT NULL"]
      [:created_at "timestamp NOT NULL"]
      [:updated_at "timestamp NOT NULL"])
    ; インデックス作成
    (sql/do-commands
      "CREATE UNIQUE INDEX ix_users_email ON users (email)")
    (sql/do-commands
      "CREATE INDEX ix_users_email_null ON users (email) WHERE email IS NULL")
    (sql/do-commands
      "CREATE UNIQUE INDEX ix_users_provider ON users (provider, provider_user_id)")
    (sql/do-commands
      "CREATE INDEX ix_users_provider_null ON users (provider, provider_user_id) WHERE provider IS NULL AND provider_user_id IS NULL")
    (sql/do-commands
      "CREATE UNIQUE INDEX ix_users_hash ON users (hash)")))

(defn create-users-history-table
  "ユーザ履歴テーブル作成"
  []
  (sql/with-connection (config :db)
    (sql/create-table
      :users_history
      [:user_id "int NOT NULL"]
      [:email "varchar(255)"]
      [:crypted_password "varchar(255)"]
      [:provider "varchar(32)"]
      [:provider_user_id "bigint"]
      [:provider_access_token "varchar(255)"]
      [:provider_access_token_secret "varchar(255)"]
      [:hash "varchar(255) NOT NULL"]
      [:nickname "varchar(255) NOT NULL"]
      [:status "smallint NOT NULL"]
      [:admin "boolean NOT NULL"]
      [:sakura "boolean NOT NULL"]
      [:created_at "timestamp NOT NULL"]
      [:updated_at "timestamp NOT NULL"]
      [:deleted "boolean"]
      [:id "bigserial PRIMARY KEY"])
    ; インデックス作成
    (sql/do-commands
      "CREATE INDEX ix_usersh_email ON users_history (email)")
    (sql/do-commands
      "CREATE INDEX ix_usersh_email_null ON users_history (email) WHERE email IS NULL")
    (sql/do-commands
      "CREATE INDEX ix_usersh_provider ON users_history (provider, provider_user_id)")
    (sql/do-commands
      "CREATE INDEX ix_usersh_provider_null ON users_history (provider, provider_user_id) WHERE provider IS NULL AND provider_user_id IS NULL")
    (sql/do-commands
      "CREATE INDEX ix_usersh_user_id ON users_history (user_id)")
    ; トリガ作成
    (create-trigger "users")))

(defn create-profiles-table
  "プロフィールテーブル作成"
  []
  (sql/with-connection (config :db)
    (sql/create-table
      :profiles
      [:id "serial PRIMARY KEY"]
      [:user_id "int NOT NULL"]
      [:gender "smallint"]
      [:birthday "date"]
      [:income "numeric(15,4)"]
      [:twitter_user_id "bigint"]
      [:twitter_access_token "varchar(255)"]
      [:twitter_access_token_secret "varchar(255)"]
      [:facebook_user_id "bigint"]
      [:facebook_access_token "varchar(255)"]
      [:facebook_access_token_secret "varchar(255)"]
      [:created_at "timestamp NOT NULL"]
      [:updated_at "timestamp NOT NULL"])
    ; インデックス作成
    (sql/do-commands
      "CREATE UNIQUE INDEX ix_profiles_user_id ON profiles (user_id)")
    (sql/do-commands
      "CREATE INDEX ix_profiles_twitter_user_id ON profiles (twitter_user_id)")
    (sql/do-commands
      "CREATE INDEX ix_profiles_twitter_user_id_null ON profiles (twitter_user_id) WHERE twitter_user_id IS NULL")
    (sql/do-commands
      "CREATE INDEX ix_profiles_facebook_user_id ON profiles (facebook_user_id)")
    (sql/do-commands
      "CREATE INDEX ix_profiles_facebook_user_id_null ON profiles (facebook_user_id) WHERE facebook_user_id IS NULL")
    (sql/do-commands
      "CREATE INDEX ix_profiles_gender ON profiles (gender)")
    (sql/do-commands
      "CREATE INDEX ix_profiles_birthday ON profiles (birthday)")
    (sql/do-commands
      "CREATE INDEX ix_profiles_income ON profiles (income)")))

(defn create-profiles-history-table
  "プロフィール履歴テーブル作成"
  []
  (sql/with-connection (config :db)
    (sql/create-table
      :profiles_history
      [:profile_id "int NOT NULL"]
      [:user_id "int NOT NULL"]
      [:gender "smallint"]
      [:birthday "date"]
      [:income "numeric(15,4)"]
      [:twitter_user_id "bigint"]
      [:twitter_access_token "varchar(255)"]
      [:twitter_access_token_secret "varchar(255)"]
      [:facebook_user_id "bigint"]
      [:facebook_access_token "varchar(255)"]
      [:facebook_access_token_secret "varchar(255)"]
      [:created_at "timestamp NOT NULL"]
      [:updated_at "timestamp NOT NULL"]
      [:deleted "boolean"]
      [:id "bigserial PRIMARY KEY"])
    ; インデックス作成
    (sql/do-commands
      "CREATE INDEX ix_profileh_profile_id ON profiles_history (profile_id)")
    (sql/do-commands
      "CREATE INDEX ix_profileh_user_id ON profiles_history (user_id)")
    ; トリガ作成
    (create-trigger "profiles")))

(defn create-business-table
  "職業テーブル作成"
  []
  (sql/with-connection (config :db)
    (sql/create-table
      :business
      [:id "int NOT NULL"]
      [:user_id "int NOT NULL"]
      [:note "varchar(255)"]
      [:created_at "timestamp NOT NULL"]
      [:updated_at "timestamp NOT NULL"])
    ; インデックス作成
    (sql/do-commands
      "CREATE UNIQUE INDEX ix_business_id ON business (id, user_id)")
    (sql/do-commands
      "CREATE INDEX ix_business_user_id ON business (user_id, id)")))

(defn create-business-history-table
  "職業履歴テーブル作成"
  []
  (sql/with-connection (config :db)
    (sql/create-table
      :business_history
      [:business_id "int NOT NULL"]
      [:user_id "int NOT NULL"]
      [:note "varchar(255)"]
      [:created_at "timestamp NOT NULL"]
      [:updated_at "timestamp NOT NULL"]
      [:deleted "boolean"]
      [:id "bigserial PRIMARY KEY"])
    ; インデックス作成
    (sql/do-commands
      "CREATE INDEX ix_businessh_business_id ON business_history (business_id)")
    (sql/do-commands
      "CREATE INDEX ix_businessh_user_id ON business_history (user_id)")
    ; トリガ作成
    (create-trigger "business")))

(defn create-actions-table
  "アクションテーブル作成"
  []
  (sql/with-connection (config :db)
    (sql/create-table
      :actions
      [:token "varchar(255) PRIMARY KEY"]
      [:limit_time "timestamp NOT NULL"]
      [:type "int NOT NULL"]
      [:status "int NOT NULL"]
      [:user_id "int"]
      [:note "text"]
      [:created_at "timestamp NOT NULL"]
      [:updated_at "timestamp NOT NULL"])
    ; インデックス作成
    (sql/do-commands
      "CREATE UNIQUE INDEX ix_actions_token_time ON actions (token, limit_time)")
    (sql/do-commands
      "CREATE UNIQUE INDEX ix_actions_user_id_time ON actions (user_id, limit_time)")))

(defn create-actions-history-table
  "アクション履歴テーブル作成"
  []
  (sql/with-connection (config :db)
    (sql/create-table
      :actions_history
      [:token "varchar(255) NOT NULL"]
      [:limit_time "timestamp NOT NULL"]
      [:type "int NOT NULL"]
      [:status "int NOT NULL"]
      [:user_id "int"]
      [:note "text"]
      [:created_at "timestamp NOT NULL"]
      [:updated_at "timestamp NOT NULL"]
      [:deleted "boolean"]
      [:id "bigserial PRIMARY KEY"])
    ; インデックス作成
    (sql/do-commands
      "CREATE INDEX ix_actionsh_token_time ON actions_history (token, limit_time)")
    (sql/do-commands
      "CREATE INDEX ix_actionsh_user_id_time ON actions_history (user_id, limit_time)")
    ; トリガ作成
    (create-trigger "actions")))

(defn create-records-table
  "記録テーブル作成"
  []
  (sql/with-connection (config :db)
    (sql/create-table
      :records
      [:id "bigserial PRIMARY KEY"]
      [:user_id "int NOT NULL"]
      [:record_date "date NOT NULL"]
      [:come_time "timestamp NOT NULL"]
      [:leave_time "timestamp"]
      [:note "text"]
      [:created_at "timestamp NOT NULL"]
      [:updated_at "timestamp NOT NULL"])
    ; インデックス作成
    (sql/do-commands
      "CREATE UNIQUE INDEX ix_records_user_id ON records (user_id, record_date)")
    (sql/do-commands
      "CREATE UNIQUE INDEX ix_records_date ON records (record_date, user_id)")))

(defn create-records-history-table
  "記録履歴テーブル作成"
  []
  (sql/with-connection (config :db)
    (sql/create-table
      :records_history
      [:record_id "bigint NOT NULL"]
      [:user_id "int NOT NULL"]
      [:record_date "date NOT NULL"]
      [:come_time "timestamp NOT NULL"]
      [:leave_time "timestamp"]
      [:note "text"]
      [:created_at "timestamp NOT NULL"]
      [:updated_at "timestamp NOT NULL"]
      [:deleted "boolean"]
      [:id "bigserial PRIMARY KEY"])
    ; インデックス作成
    (sql/do-commands
      "CREATE INDEX ix_recordh_user_id ON records_history (user_id, record_date)")
    (sql/do-commands
      "CREATE INDEX ix_recordh_record_date ON records_history (record_date, user_id)")
    ; トリガ作成
    (create-trigger "records")))

(defn create-access-log-table
  "アクセスログテーブル作成"
  []
  (sql/with-connection (config :db)
    (sql/create-table
      :access_log
      [:id "bigserial PRIMARY KEY"]
      [:access_time "timestamp NOT NULL"]
      [:user_id "int"]
      [:useragent "text"]
      [:remote_addr "varchar(255)"]
      [:uri "text"]
      [:cokkie "text"]
      [:query_string "text"]
      [:request_string "text"]
      [:params "text"]
      [:referer "text"]
      [:accept_language "varchar(256)"]
      [:host "varchar(512)"]
      [:server_name "varchar(255)"]
      [:server_port "int"]
      [:status "int"]
      [:response_headers "text"]
      [:response_body "text"])
    ; インデックス作成
    (sql/do-commands
      "CREATE INDEX ix_access_log_time ON access_log (access_time, user_id)")
    (sql/do-commands
      "CREATE INDEX ix_access_log_useragent ON access_log (access_time, user_id)")))

(defn create-all-tables
  "全てのテーブルを作成"
  []
  (create-users-table)
  (create-users-history-table)
  (create-profiles-table)
  (create-profiles-history-table)
  (create-business-table)
  (create-business-history-table)
  (create-actions-table)
  (create-actions-history-table)
  (create-records-table)
  (create-records-history-table)
  (create-access-log-table))

(defn insert-dummy-users
  "さくらユーザを作成する"
  []
  (let [id (atom -1)]
    (dorun
      (for [name const/name-list]
        (let [gender (nth const/gender-list (let [n (rand-int 5)] (if (>= n 3) 1 n)))
              business (nth const/business-list (rand-int 9))
              birth-year (+ 1970 (rand-int 24))
              birth-month (inc (rand-int 12))
              birth-day (inc (rand-int 28))
              income (+ 2000000 (rand-int 7000000))]
          (sql/with-connection (config :db)
            (sql/do-commands
              (str "INSERT INTO users VALUES(" @id ", 'a" @id "@a.com', '', NULL, NULL, NULL, NULL, '" (lauth/create-random-token) "', "
                "'" name "', " const/user-normal ", false, true, NOW(), NOW())")
              (str "INSERT INTO profiles VALUES(" @id ", " @id ", " (:id gender) ", '"
                birth-year "-" birth-month "-" birth-day "', " income ", NULL, NULL, NULL, NULL, NULL, NULL, NOW(), NOW())")
              (str "INSERT INTO business VALUES(" (:id business) ", " @id ", NULL, NOW(), NOW())")))
          (reset! id (dec @id)))))))

(defn drop-all-tables
  "全テーブル削除"
  []
  (drop-table "users")
  (drop-table "profiles")
  (drop-table "business")
  (drop-table "actions")
  (drop-table "records")
  (sql/with-connection (config :db)
    (sql/do-commands
      (str "DROP TABLE IF EXISTS access_log"))))

(defn -main []
  (println "Migrating...")(flush)
  (create-all-tables)
  (insert-dummy-users)
  (println "Done."))
