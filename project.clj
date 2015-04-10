(defproject worker "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://www.syatiku.net"
  :min-lein-version "2.0.0"
  :java-agents [[com.newrelic.agent.java/newrelic-agent "2.19.0"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/math.numeric-tower "0.0.2"]
                 [compojure "1.1.8"]
                 [hiccup "1.0.5"]
                 [hiccup-bootstrap-elements "3.1.1-SNAPSHOT"]
                 [ring-server "0.3.1"]
                 [postgresql/postgresql "9.1-901.jdbc4"]
                 [org.clojure/java.jdbc "0.3.4"]
                 [lib-noir "0.8.4"]
                 [korma "0.3.2"]
                 [hiccup "1.0.5"]
                 [com.postspectacular/rotor "0.1.0"]
                 [org.apache.commons/commons-email "1.3.3"]
                 [com.taoensso/timbre "3.2.1"]
                 [org.clojure/data.codec "0.1.0"]
                 [ring/ring-jetty-adapter "1.3.0"]
                 [sonian/carica "1.1.0"]
                 [clj-time "0.7.0"]
                 [org.twitter4j/twitter4j-core "4.0.2"]]
  :plugins [[lein-pprint "1.1.1"]
            [lein-ring "0.8.11"]]
  :ring {:handler worker.handler/app
         :init worker.handler/init
         :destroy worker.handler/destroy}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
