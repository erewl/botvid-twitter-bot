(ns twitter-bot.core
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clj-time.format :as format]
            [clj-time.core :as time]
            [clj-http.client :as client]
            [twttr.api :as api]
            [twttr.auth :refer [env->UserCredentials]]
            [twitter-bot.files.read :as fl]
            [overtone.at-at :as overtone]
            ; [clojure.tools.logging :as log]
            ))

(def custom-formatter (format/formatter "yyyy-MM-dd"))

(defn parseDate [dateString]
  (format/parse custom-formatter dateString))

(defn prepare-data [header data countryIsoCode]
  (let [countryIndex (.indexOf header "location")
        isoCodeIndex (.indexOf header "iso_code")
        newCasesIndex (.indexOf header "new_cases")
        newDeathsIndex (.indexOf header "new_deaths")
        dateIndex (.indexOf header "date")
        totalCasesIndex (.indexOf header "total_cases")
        totalDeathsIndex (.indexOf header "total_deaths")
        ; newTestsIndex (.indexOf header "new_tests")
        ; totalTestsIndex (.indexOf header "total_tests")
        ;; data prep
        filtered-data (filter (fn [element] (= countryIsoCode (nth element isoCodeIndex))) data)
        preparedData (map (fn [elements] {:date (parseDate (nth elements dateIndex))
                                          :isoCode (nth elements isoCodeIndex)
                                          :country (nth elements countryIndex)
                                          :newCases (int (Double/parseDouble (nth elements newCasesIndex)))
                                          :totalCases (int (Double/parseDouble (nth elements totalCasesIndex)))
                                          :newDeaths (int (Double/parseDouble (nth elements newDeathsIndex)))
                                          :totalDeaths (int (Double/parseDouble (nth elements totalDeathsIndex)))
                                      ; :newTests (nth elements newTestsIndex)
                                      ; :totalTests (nth elements totalTestsIndex)
                                          })filtered-data)]
    preparedData))

(defn getFile [url fileName]
  (let [currentDate (format/unparse custom-formatter (time/now))
        filePath (str  "data/" currentDate "_" fileName ".csv")]
    (if (.exists (io/file filePath))
      (let []
        (println "file exists using this one: " filePath)
        filePath)
      (let []
        (clojure.java.io/copy
         (:body (client/get url {:as :stream}))
         (java.io.File. filePath))
        filePath))))

(defn writeTweet [data]
  (str "Covid-19 Numbers - " (:country data) " - " (format/unparse custom-formatter (:date data))
       "\n\nNew Cases:\t  " (:newCases data)
       "\nTotal Cases:\t  " (:totalCases data)
       "\n\nNew Deaths:\t  " (:newDeaths data)
       "\nTotal Deaths:\t  " (:totalDeaths data)
       "\n\nStay healthy and stay safe!"))

(def my-pool (overtone/mk-pool))

(defn tweet [creds data]
  (println (writeTweet data))
  (api/statuses-update creds :params {:status (writeTweet data)}))

(defn devtweet [creds data]
  (println (writeTweet data)))

;; "https://covid.ourworldindata.org/data/owid-covid-data.csv"
(defn prepareData [link countryIsoCode]
  (let [download     (getFile link "covid-data")
        data-file download
        csv (fl/read-file data-file)
        header (first csv)
        data (prepare-data header  (drop 1 csv) countryIsoCode)]
    (last data)))

(defn -main
  [& args]
  (let [;; loading config file
        config (clojure.edn/read-string (slurp (io/resource "config.edn")))
        creds (env->UserCredentials)]
    (println "Started up")
    (overtone/every
     (* 1000 60 60 24)
     #(tweet creds (prepareData  "https://covid.ourworldindata.org/data/owid-covid-data.csv" "NLD")) my-pool)))