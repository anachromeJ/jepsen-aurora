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
             [zookeeper :as zk]]
            [jepsen.os.debian :as debian]))

(defn install!
  ""
  []
  (c/exec "sudo apt-get update")
  (c/exec "sudo apt-get install build-essential")
  (c/exec "git clone http://git-wip-us.apache.org/repos/asf/aurora.git")
  (c/exec "cd aurora")
  (c/exec "./gradlew distZip"))

(defn db
  "Installs Aurora"
  []
  (reify db/DB
    (setup! [_ test node]
      (install!)
      )

    (teardown! [_ test node]
      )
))

(defn simple-test
  []
  (assoc tests/noop-test
         :name      "aurora"
         :os        debian/os
         :db        (db)
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
         :ssh       {:username "root"
                     :private-key-path "/home/vagrant/.ssh/id_rsa"}))

