(ns jepsen.aurora
  "Sets up Aurora"
  (:require [clojure.tools.logging :refer :all]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clj-time.core :as time]
            [clj-time.format :as time.format]            
            [jepsen 
             [client :as client]
             [db :as db]
             [tests :as tests]
             [control :as c :refer [|]]
             [checker :as checker]
             [nemesis :as nemesis]
             [generator :as gen]
             [util :as util :refer [meh timeout]]
             [zookeeper :as zk]
             [mesos :as mesos]]
            [jepsen.control.util :as cu]
            [jepsen.os.debian :as debian]
            [jepsen.aurora.checker :refer [checker epsilon-forgiveness]]))

(def job-script-dir "/tmp/aurora-jobs/")
(def job-result-dir "/tmp/aurora-test/")

(defn install!
  "Installs Java 8 and Aurora scheduler"
  [test node]
  (info node "Installing Java 8 and Aurora")
  (c/su
   (c/exec :curl :-L "https://raw.githubusercontent.com/jchli/jepsen-aurora/master/scripts/install-aurora.sh" :-o "/install-aurora.sh")
   (c/exec :bash "/install-aurora.sh")
   (c/exec :curl :-L "https://raw.githubusercontent.com/jchli/jepsen-aurora/master/scripts/aurora-scheduler.sh" :-o "/aurora-scheduler.sh")
   (c/exec :curl :-L "https://raw.githubusercontent.com/jchli/jepsen-aurora/master/scripts/add-job.sh" :-o "/add-job.sh")
   (c/exec :mkdir :-p job-result-dir)
   (c/exec :chmod :777 job-result-dir)
   ;; (c/exec :mkdir :-p "/etc/aurora")
   ;; (c/exec :curl :-L "https://raw.githubusercontent.com/jchli/jepsen-aurora/master/scripts/clusters.json" :-o "/etc/aurora/clusters.json")
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
      (c/su (c/exec :mkdir :-p job-result-dir))
      (start! test node)
      )
      
      (teardown! [_ test node]
        (info node "stopping aurora")
        (c/su (cu/grepkill "aurora-scheduler"))
        (db/teardown! mesos test node)
        ;; (c/su (c/exec :rm :-rf job-result-dir))
      )

      db/LogFiles
      (log-files [_ test node]
        (db/log-files mesos test node))
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
  (shell/sh "/jepsen/jepsen-aurora/scripts/add-job.sh" 
          (str (:name job))
          (str (:duration job))))

(def formatter (time.format/formatters :date-time))

(defn parse-file-time
  "Date can (maybe depending on locale) emit datetimes with commas to separate
  fractional seconds, which (even though it's valid ISO8601) confuses
  clj-time, so we substitute dots for commas before parsing."
  [t]
  (when t
    (time.format/parse formatter (str/replace t \, \.))))

(defn parse-file
  "Given a node name and a run logfile with a name, start time, and end time,
  returns a map for that run."
  [node file-str]
  (let [[name start end] (str/split file-str #"\n")]
    {:node  node
     :name  (Long/parseLong (subs (str/trim name) 3)) ;; take away the first three characters ('job')
     :start (parse-file-time start)
     :end   (parse-file-time end)}))

(defn read-runs
  "Returns all runs from all nodes."
  [test]
  (->> (c/on-many
         (:nodes test)
         (->> (cu/ls-full job-result-dir)
              (pmap (partial c/exec :cat))
              (mapv (partial parse-file c/*host*))))
       vals
       (reduce concat)))

(defn add-job
  "Generator for creating new jobs."
  []
  (let [id (atom 0)]
    (reify gen/Generator
      (op [_ test process]
        (let [head-start  10 ; Schedule a bit in the future
              duration    (rand-int 10)
              epsilon     (+ 10 (rand-int 20))
              ; (Aurora) won't schedule tasks concurrently, so we ensure they'll
              ; never overlap.
              interval    (+ 1
                             duration
                             epsilon
                             epsilon-forgiveness)]
        {:type   :invoke
         :f      :add-job
         :value  {:name     (swap! id inc)
                  :start    (time/plus (time/now) (time/seconds head-start))
                  :count    300 ;; actually running infinitely
                  :duration duration
                  :epsilon  epsilon
                  :interval interval}})))))

(defrecord Client [node]
  client/Client
  (setup! [this test node]
    (assoc this :node node))

  (invoke! [this test op]
   (timeout 10000 (assoc op :type :info, :value :timed-out)
             (try
               (case (:f op)
                 :add-job (do (add-job! node (:value op))
                              (assoc op :type :ok))
                 :read    (do ; (info (with-out-str (pprint (jobs node))))
                              (assoc op
                                     :type :ok
                                     :value (read-runs test))))
               )))

  (teardown! [_ test]))

(defn resurrection-hub
  "Mesos and Aurora like to crash all the time. We have to bring them back to
  life regularly. Wraps another nemesis."
  [nemesis]
  (reify client/Client
    (setup! [this test node]
      (resurrection-hub (client/setup! nemesis test node)))

    (invoke! [this test op]
      (if (not= :resurrect (:f op))
        (client/invoke! nemesis test op)
        (do (c/on-many (:nodes test)
                       (let [node (keyword c/*host*)]
                         (mesos/start-master! test node)
                         (mesos/start-slave! test node)
                         (start! test node)))
            (assoc op :value :resurrection-complete))))

    (teardown! [this test]
      (client/teardown! nemesis test))))


(defn simple-test
  [mesos-version]
  (assoc tests/noop-test
         :name      "aurora"
         :os        debian/os
         :db        (db mesos-version)
         :client    (->Client nil)
         :generator (gen/phases
                     (->> (add-job)
                          (gen/delay 60)
                          (gen/stagger 30)
                          (gen/nemesis
                           (gen/seq (cycle [(gen/sleep 120)
                                            {:type :info, :f :start}
                                            (gen/sleep 120)
                                            {:type :info, :f :stop}
                                            {:type :info, :f :resurrect}])))
                          (gen/time-limit 350))
                     (gen/nemesis (gen/once {:type :info, :f :stop}))
                     (gen/nemesis (gen/once {:type :info, :f :resurrect}))
                     (gen/log "Waiting for executions")
                     (gen/sleep 100)
                     (gen/clients
                      (gen/once
                       {:type :invoke, :f :read})))
         :nemesis   (resurrection-hub
                     (nemesis/partition-random-halves))
         :checker   (checker/compose
                     {:aurora (checker)
                      :perf (checker/perf)})
))

