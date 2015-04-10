(ns worker.models.db
  (:require [clojure.java.jdbc :as sql]
            [korma.db :refer [defdb]]
            [korma.core :as korma])
  (:use [carica.core]))

(def con nil)

(defdb korma-db (config :db))
