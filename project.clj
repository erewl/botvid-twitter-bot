(defproject twitter-bot "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  ;; :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License v1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.csv "1.0.0"]
                 [org.clojure/tools.logging "1.1.0"]
                 [clj-http "3.10.1"]
                 [clj-time "0.15.2"]
                 [twttr "3.2.3"]
                 [com.novemberain/monger "3.1.0"]
                 [overtone/at-at "1.2.0"]
                 [environ "1.2.0"]
                 [clojurewerkz/quartzite "2.1.0"]
                 [metosin/malli "0.0.1-SNAPSHOT"]]
  ;; :hooks [environ.leiningen.hooks]
  :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/slf4j-factory"]
  :uberjar-name "botvid19-standalone.jar"
  :plugins [[lein-environ "1.0.0"]]
  :main twitter-bot.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
