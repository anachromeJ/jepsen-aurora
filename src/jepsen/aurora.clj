(ns jepsen.aurora
  "Sets up Aurora"
  (:require [jepsen 
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
  "Installs java 8 and aurora scheduler"
  []
  (c/su
   (c/exec :curl :-L "https://raw.githubusercontent.com/jchli/jepsen-aurora/master/aurora/install-aurora.sh" :-o "/install-aurora.sh")
   (c/exec :bash "/install-aurora.sh")
   (c/exec :curl :-L "https://raw.githubusercontent.com/jchli/jepsen-aurora/master/aurora/aurora-scheduler.sh" :-o "/aurora-scheduler.sh")))

(defn start!
  [test node]
  (c/su
   (c/exec ://usr/local/aurora-scheduler/bin/aurora-scheduler
           :-mesos_master_address (mesos/zk-uri test)
           :-backup_dir "/usr/local/aurora-scheduler/backup"
           :-serverset_path "/aurora/scheduler"
           :-cluster_name "test"
           :-zk_endpoints "localhost:2181")))


(defn db
  "Installs Aurora scheduler"
  [mesos-version]
  (let [mesos (mesos/db mesos-version)]
    (reify db/DB
      (setup! [_ test node]
        
      (db/setup! mesos test node)
      (install!)
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

