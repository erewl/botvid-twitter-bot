(ns twitter-bot.files.read
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]))

(defn read-file [file-path]
  (with-open [reader (io/reader file-path)]
    (doall
     (csv/read-csv reader))))