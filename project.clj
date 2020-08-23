(defproject twitter-bot "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.csv "1.0.0"]
                 [clj-http "3.10.1"]
                 [clj-time "0.15.2"]
                 [twttr "3.2.3"]
                 [com.novemberain/monger "3.1.0"]
                 [overtone/at-at "1.2.0"]
                 [environ "1.2.0"]
                 [clojurewerkz/quartzite "2.1.0"]
                 [org.clojure/tools.logging "1.1.0"]]
  :plugins [[lein-environ "1.0.0"]]
  :main twitter-bot.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
