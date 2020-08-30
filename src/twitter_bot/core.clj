(ns twitter-bot.core
  (:gen-class)
  (:import [java.time Instant]
           [java.time Instant Duration])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clj-time.format :as format]
            [clj-time.core :as time]
            [clj-http.client :as client]
            [twttr.api :as api]
            [twttr.auth :refer [env->UserCredentials]]
            [twitter-bot.files.read :as fl]
            [overtone.at-at :as overtone]
            ;; quartzite
            [clojurewerkz.quartzite.scheduler :as qs]
            [clojurewerkz.quartzite.jobs :refer [defjob] :as j]
            [clojurewerkz.quartzite.triggers :as t]
            [clojurewerkz.quartzite.schedule.cron :refer [schedule cron-schedule]]))

(def custom-formatter (format/formatter "yyyy-MM-dd"))

(defn parseDate [dateString]
  (format/parse custom-formatter dateString))

(defn extractData [header data countryIsoCode]
  (let [countryIndex (.indexOf header "location")
        isoCodeIndex (.indexOf header "iso_code")
        newCasesIndex (.indexOf header "new_cases")
        newDeathsIndex (.indexOf header "new_deaths")
        dateIndex (.indexOf header "date")
        totalCasesIndex (.indexOf header "total_cases")
        totalDeathsIndex (.indexOf header "total_deaths")
        ;; data prep
        filtered-data (filter (fn [element] (= countryIsoCode (nth element isoCodeIndex))) data)
        preparedData (map (fn [elements] {:date (parseDate (nth elements dateIndex))
                                          :isoCode (nth elements isoCodeIndex)
                                          :country (nth elements countryIndex)
                                          :newCases (int (Double/parseDouble (nth elements newCasesIndex)))
                                          :totalCases (int (Double/parseDouble (nth elements totalCasesIndex)))
                                          :newDeaths (int (Double/parseDouble (nth elements newDeathsIndex)))
                                          :totalDeaths (int (Double/parseDouble (nth elements totalDeathsIndex)))}) filtered-data)]
    preparedData))

(defn deleteOutdatedFilesExcept [path fileName]
  (let [directory (clojure.java.io/file path)
        files (file-seq directory)
        filesToDelete (filter (fn [file] (not (.contains (.getPath file) fileName))) files)
        deletedFiles (map (fn [file] (.delete file)) filesToDelete)]
    ;; filtering out the failed delete attempts
    (count (filter (fn [f] f) deletedFiles))))

(def dataDirectory "data")

(defn getFile [url fileName]
  (let [currentDate (format/unparse custom-formatter (time/now))
        filePath (str   "data/" currentDate "_" fileName ".csv")
        deleteCount (deleteOutdatedFilesExcept dataDirectory filePath)]
    (println (str "Deleted " deleteCount " outdated files!"))
    (if (.exists (io/file filePath))
      (let []
        (println "Using existing files: " filePath)
        filePath)
      (let []
        (clojure.java.io/copy
         (:body (client/get url {:as :stream}))
         (java.io.File. filePath))
        filePath))))

(defn moreOrLessThanYesterDay [number]
  (cond
    (> number 0) (str " (" number " more than yesterday)")
    (< number 0) (str " (" (Math/abs number) " less than yesterday)")
    :else " (same as yesterday)"))

(defn writeTweet [today yesterday]
  (str "Covid-19 Numbers - " (:country today) " - " (format/unparse custom-formatter (:date today))
       "\n\nNew Cases:\t  " (:newCases today) (moreOrLessThanYesterDay (- (:newCases today) (:newCases yesterday)))
       "\nTotal Cases:\t  " (:totalCases today)
       "\n\nNew Deaths:\t  " (:newDeaths today) (moreOrLessThanYesterDay (- (:newDeaths today) (:newDeaths yesterday)))
       "\nTotal Deaths:\t  " (:totalDeaths today)
       "\n\nStay healthy and stay safe!"))

(def my-pool (overtone/mk-pool))

(defn postTweet [creds today yesterday]
  (println (writeTweet today yesterday))
  (println (api/statuses-update creds :params {:status (writeTweet today yesterday)})))

(defn devtweet [creds today yesterday]
  (println (writeTweet today yesterday)))

;; "https://covid.ourworldindata.org/data/owid-covid-data.csv"
(defn prepareData [link countryIsoCode]
  (let [download     (getFile link "covid-data")
        data-file download
        csv (fl/read-file data-file)
        header (first csv)
        data (extractData header  (drop 1 csv) countryIsoCode)]
    data))

(defn tweetDailyNumbersForCountry [isoCode]
  (let [creds (env->UserCredentials)
        data (prepareData  "https://covid.ourworldindata.org/data/owid-covid-data.csv" isoCode)]
    (if (some? data)
      (let [[yesterday today] (take-last 2 data)]
        (postTweet creds today yesterday))
      (println (str "Unable to find data for isoCode " isoCode)))))

(defjob dailyTweetJob
  [ctx]
  (tweetDailyNumbersForCountry "NLD"))

(defn -main
  [& args]
  (let [;; loading config file
        config (clojure.edn/read-string (slurp (io/resource "config.edn")))
        ;; scheduler
        s   (-> (qs/initialize) qs/start)
        ;; jobs
        job (j/build
             (j/of-type dailyTweetJob)
             (j/with-identity (j/key "jobs.dailyTweet.1")))
        ;; cron trigger
        trigger (t/build
                 (t/with-identity (t/key "triggers.1"))
                 (t/start-now)
                 (t/with-schedule (schedule
                                   (cron-schedule "0 0 13 1/1 * ? *"))))]
    (println "Started up")
    (qs/schedule s job trigger)
    ))
