(ns jepsen.aurora
  "Sets up Aurora"
  (:require [clojure.tools.logging :refer :all]
            [jepsen 
             [client :as client]
             [db :as db]
             [tests :as tests]
             [control :as c :refer [|]]
             [checker :as checker]
             [generator :as gen]
             [util :as util :refer [meh timeout]]
             [zookeeper :as zk]
             [mesos :as mesos]]
            [jepsen.os.debian :as debian]))

(def parent-job-dir "/tmp/aurora-jobs/")
(def slave-job-dir "/tmp/aurora-test/")

(defn install!
  "Installs Java 8 and Aurora scheduler"
  [test node]
  (info node "Installing Java 8 and Aurora")
  (c/su
   (c/exec :curl :-L "https://raw.githubusercontent.com/jchli/jepsen-aurora/master/scripts/install-aurora.sh" :-o "/install-aurora.sh")
   (c/exec :bash "/install-aurora.sh")
   (c/exec :curl :-L "https://raw.githubusercontent.com/jchli/jepsen-aurora/master/scripts/aurora-scheduler.sh" :-o "/aurora-scheduler.sh")
   ))

(defn start!
  [test node]
  (info node "starting aurora-scheduler")
  (c/su
   (c/exec :bash "/aurora-scheduler.sh")))

(defn db
  "Installs Aurora scheduler"
  [mesos-version]
  (let [mesos (mesos/db mesos-version)]
    (reify db/DB
      (setup! [_ test node]
        
      (db/setup! mesos test node)
      (install! test node)
      (start! test node)
      )
      
      (teardown! [_ test node]
        )

      db/LogFiles
      (log-files [_ test node]
        (db/log-files mesos test node))
        ;; (concat (db/log-files mesos test node)
        ;;         ["/var/log/messages"]))
      )))

; Job representation
;
; Jobs are maps with the following keys:
; :name     - A globally unique name for the job
; :start    - A datetime for when the job begins
; :interval - How long between runs in seconds
; :count    - How many times should we repeat the job
; :epsilon  - Allowable tolerance, in seconds, for scheduling
; :duration - How long should a run take, in seconds?

(defn add-job!
  "Submits a new job to Aurora"
  [node job]
  (c/exec :bash "add-job.sh" (:name job) (:duration job)))

(defn simple-test
  [mesos-version]
  (assoc tests/noop-test
         :name      "aurora"
         :os        debian/os
         :db        (db mesos-version)
         ;; :client    (->Client nil)
         ;; :generator (gen/phases
         ;;             (->> (add-job)
         ;;                  (gen/delay 30)
         ;;                  (gen/stagger 30)
         ;;                  (gen/nemesis
         ;;                   (gen/seq (cycle [(gen/sleep 200)
         ;;                                    {:type :info, :f :start}
         ;;                                    (gen/sleep 200)
         ;;                                    {:type :info, :f :stop}
         ;;                                    {:type :info, :f :resurrect}])))
         ;;                  (gen/time-limit 450))
         ;;             (gen/nemesis (gen/once {:type :info, :f :stop}))
         ;;             (gen/nemesis (gen/once {:type :info, :f :resurrect}))
         ;;             (gen/log "Waiting for executions")
         ;;             (gen/sleep 400)
         ;;             (gen/clients
         ;;              (gen/once
         ;;               {:type :invoke, :f :read})))
         ;; :nemesis   (resurrection-hub
         ;;             (nemesis/partition-random-halves))
         ;; :checker   (checker/compose
         ;;             {:chronos (checker)
         ;;              :perf (checker/perf)})
))

