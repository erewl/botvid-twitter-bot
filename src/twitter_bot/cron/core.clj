(ns twitter-bot.cron.core
    (:require [clojurewerkz.quartzite.scheduler :as qs]
              [mount.core :as mount]))

; (mount/defstate scheduler
;   :start (qs/start (qs/initialize))
;   :stop (qs/shutdown scheduler))

