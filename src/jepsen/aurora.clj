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
  ; (debian/add-repo! :webupd8team
  ;                   "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main"
  ;                   "hkp://keyserver.ubuntu.com:80"
  ;                   "EEA14886")
  ; (debian/add-repo! :webupd8team-src
  ;                   "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main")
  ; (debian/install {:oracle-java8-installer "1.8.0_66"})
  (c/su
   (c/exec :echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" :| :tee "/etc/apt/sources.list.d/webupd8team-java.list")
   (c/exec :echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" :| :tee :-a "/etc/apt/sources.list.d/webupd8team-java.list")
   (c/exec :apt-key "adv" :--keyserver "hkp://keyserver.ubuntu.com:80" :--recv-keys "EEA14886")
   (c/exec :apt-get "update")
   (c/exec :echo "oracle-java8-installer shared/accepted-oracle-license-v1-1 select true" :| "/usr/bin/debconf-set-selections")
   (c/exec :apt-get :install "oracle-java8-installer" :-y :--force-yes)

   (c/exec :curl :-L "https://github.com/jchli/jepsen-aurora/raw/master/aurora/dist/distributions/aurora-scheduler-0.11.0-SNAPSHOT.zip" :-o "aurora-scheduler.zip")
   (c/exec :unzip :-n "aurora-scheduler.zip" :-d "/usr/local")
   (c/exec :mv :-f "/usr/local/aurora-scheduler-0.11.0-SNAPSHOT" "/usr/local/aurora-scheduler")))
;; (c/exec :ln :-nfs "aurora-scheduler" "/usr/local/aurora-scheduler")))

(defn start!
  [test node]
  (c/su
   (c/exec :/usr/local/aurora-scheduler/bin/aurora_scheduler
           :-mesos_master_address (mesos/zk-uri test)
           :-backup_dir "/usr/local/aurora-scheduler/backup"
           :-serverset_path "/aurora/scheduler"
           :-cluster_name "test")))


(defn db
  "Installs Aurora"
  [mesos-version]
  (let [mesos (mesos/db mesos-version)]
    (reify db/DB
      (setup! [_ test node]
        
      (db/setup! mesos test node)
      (install!)
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

