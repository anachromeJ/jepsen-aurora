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

(defn install!
  "Installs Java 8 and Aurora scheduler"
  [test node]
  (info node "Installing Java 8 and Aurora")
  (c/su
   (c/exec :curl :-L "https://raw.githubusercontent.com/jchli/jepsen-aurora/master/aurora/install-aurora.sh" :-o "/install-aurora.sh")
   (c/exec :bash "/install-aurora.sh")
   ))

(defn configure!
  [test node]
  (info node "Configuring Aurora")
  (c/su
   (c/exec :curl :-L "https://raw.githubusercontent.com/jchli/jepsen-aurora/master/aurora/aurora-scheduler.sh" :-o "/aurora-scheduler.sh")
   (c/exec :mesos-log :intialize :--path "/usr/local/aurora-scheduler/mesos-log")))

(defn start!
  [test node]
  (info node "starting aurora-scheduler")
  (c/su
   (c/exec :bash "/aurora-scheduler.sh" :&)))

(defn db
  "Installs Aurora scheduler"
  [mesos-version]
  (let [mesos (mesos/db mesos-version)]
    (reify db/DB
      (setup! [_ test node]
        
      (db/setup! mesos test node)
      (install! test node)
      (configure! test node)
      (start! test node)
      )
      
      (teardown! [_ test node]
        )
      )))

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

