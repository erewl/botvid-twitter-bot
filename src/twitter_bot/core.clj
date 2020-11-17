(ns twitter-bot.core
  (:gen-class)
  (:import [java.time Instant Duration LocalDate])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clj-time.format :as format]
            [clj-time.core :as time]
            [clj-http.client :as client]
            ;; [clojure.tools.logging :as log]
            [twttr.api :as api]
            [twttr.auth :refer [env->UserCredentials]]
            [twitter-bot.files.read :as fl]
            ;; quartzite
            [clojurewerkz.quartzite.scheduler :as qs]
            [clojurewerkz.quartzite.jobs :refer [defjob] :as j]
            [clojurewerkz.quartzite.triggers :as t]
            [clojurewerkz.quartzite.schedule.cron :refer [schedule cron-schedule]]))

(use 'clojure.tools.logging)


(def custom-formatter (format/formatter "yyyy-MM-dd"))
(def java-formatter (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd"))

(defn parseDate [dateString]
  (format/parse custom-formatter dateString))

(defn extractData [header data countryIsoCode todayDate yesterdayDate]
  (let [countryIndex (.indexOf header "location")
        isoCodeIndex (.indexOf header "iso_code")
        newCasesIndex (.indexOf header "new_cases")
        newDeathsIndex (.indexOf header "new_deaths")
        dateIndex (.indexOf header "date")
        totalCasesIndex (.indexOf header "total_cases")
        totalDeathsIndex (.indexOf header "total_deaths")
        ;; data prep
        filtered-data (filter (fn [element] (and (= countryIsoCode (nth element isoCodeIndex))
                                                 (or (= todayDate (nth element dateIndex))
                                                     (= yesterdayDate (nth element dateIndex))))) data)
        preparedData (map (fn [elements] {:date (nth elements dateIndex)
                                          :isoCode (nth elements isoCodeIndex)
                                          :country (nth elements countryIndex)
                                          :newCases (int (Double/parseDouble (nth elements newCasesIndex)))
                                          :totalCases (int (Double/parseDouble (nth elements totalCasesIndex)))
                                          :newDeaths (int (Double/parseDouble (nth elements newDeathsIndex)))
                                          :totalDeaths (int (Double/parseDouble (nth elements totalDeathsIndex)))}) filtered-data)]
    preparedData))

(def dataDirectory "./")

(defn getFile [url fileName]
  (let [filePath (str dataDirectory fileName ".csv")]
    (clojure.java.io/copy
     (:body (client/get url {:as :stream}))
     (java.io.File. filePath))
    filePath))

(defn moreOrLessThanYesterDay [number]
  (cond
    (> number 0) (str " (" number " more than the day before)")
    (< number 0) (str " (" (Math/abs number) " less than the day before)")
    :else " (same as the day before)"))

(defn writeTweet [todayData yesterdayData]
  (str "🦠 Covid19 Numbers - " (:country todayData) " - " (:date yesterdayData) " 🦠"
       "\n\nNew Cases:\t  " (:newCases todayData) (moreOrLessThanYesterDay (- (:newCases todayData) (:newCases yesterdayData)))
       "\nTotal Cases:\t  " (:totalCases todayData)
       "\n\nNew Deaths:\t  " (:newDeaths todayData) (moreOrLessThanYesterDay (- (:newDeaths todayData) (:newDeaths yesterdayData)))
       "\nTotal Deaths:\t  " (:totalDeaths todayData)
       "\n\nStay healthy and stay safe! 💪😷"))

(defn postTweet [creds today yesterday]
  (info (writeTweet today yesterday))
  (info (api/statuses-update creds :params {:status (writeTweet today yesterday)})))

(defn devTweet [creds today yesterday]
  (info (writeTweet today yesterday)))

;; "https://covid.ourworldindata.org/data/owid-covid-data.csv"
(defn prepareData [link countryIsoCode today yesterday]
  (let [download     (getFile link "covid-data")
        data-file download
        csv (fl/read-file data-file)
        header (first csv)
        data (extractData header  (drop 1 csv) countryIsoCode today yesterday)]
    data))

(defn tweetDailyNumbersForCountry [isoCode today yesterday]
  (let [creds (env->UserCredentials)
        data (prepareData  "https://covid.ourworldindata.org/data/owid-covid-data.csv" isoCode today yesterday)]
    (if (= (count data) 2)
      (let [t (first (filter (fn [e] (= today (:date e))) data))
            y (first (filter (fn [e] (= yesterday (:date e))) data))]
        (postTweet creds t y))
      (info (str "Incomplete data: " data)))))

(defn tweetWeeklyNumbersForCountry [isoCode]
  (info "poop"))

(defjob dailyTweetJob
  [ctx]
  (let [today (LocalDate/now)
        yesterday (.minusDays today 1)
        ft (.format today java-formatter)
        fy (.format yesterday java-formatter)]
    (tweetDailyNumbersForCountry "NLD" ft fy)))

(defjob weeklyTweetJob
  [ctx]
  (tweetWeeklyNumbersForCountry "NLD"))

(defn strdt [date]
  (.format
   (java.text.SimpleDateFormat. "yyyy-MM-dd" date)))

(defn manualTweet [today]
  (let [yesterday (.minusDays today 1)
        ft (.format today java-formatter)
        fy (.format yesterday java-formatter)]
    (tweetDailyNumbersForCountry "NLD" ft fy)))

(defn -main
  [& args]
  (let [;; loading config file
        config        (clojure.edn/read-string (slurp (io/resource "config.edn")))
        ;; scheduler
        s             (-> (qs/initialize) qs/start)
        ;; jobs
        job           (j/build
                       (j/of-type dailyTweetJob)
                       (j/with-identity (j/key "jobs.dailyTweet.1")))
        weeklyJob     (j/build
                       (j/of-type weeklyTweetJob)
                       (j/with-identity (j/key "jobs.weeklyTweet.1")))
        ;; cron trigger
        trigger       (t/build
                       (t/with-identity (t/key "triggers.1"))
                       (t/start-now)
                       (t/with-schedule (schedule
                                         (cron-schedule "0 0 14 1/1 * ? *"))))
        ;; every monday at 4 (UTC) +2 in summertime
        weeklyTrigger (t/build
                       (t/with-identity (t/key "triggers.2"))
                       (t/start-now)
                       (t/with-schedule (schedule
                                         (cron-schedule "0 0 14 ? * MON *"))))]

    (qs/schedule s job trigger)
    ;; (let [f (range 0 1)
    ;;       today (LocalDate/of 2020 11 13)
    ;;       days (map (fn [d] (.plusDays today d)) f)]
    ;;   (run! (fn [x] (manualTweet x)) days))
        ;; (tweetDailyNumbersForCountry "NLD" ft fy)
    ;; (qs/schedule s weeklyJob weeklyTrigger)
    ))
