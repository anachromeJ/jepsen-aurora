(ns jepsen.aurora
  "Sets up Aurora"
  (:require [clojure.tools.logging :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clj-time.core :as time]
            [clj-time.format :as time.format]            
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
            [jepsen.control.util :as cu]
            [jepsen.os.debian :as debian]
            [jepsen.aurora.checker :refer [checker epsilon-forgiveness]]))

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
      (c/su (c/exec :mkdir :-p slave-job-dir))
      (start! test node)
      )
      
      (teardown! [_ test node]
        (info node "stopping aurora")
        (c/su (cu/grepkill "aurora-scheduler"))
        (db/teardown! mesos test node)
        (c/su (c/exec :rm :-rf slave-job-dir))
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

(def formatter (time.format/formatters :date-time))

(defn interval-str
  "Given a job, emits an ISO8601 repeating interval representation."
  [job]
  (str "R" (:count job) "/"
       (time.format/unparse formatter (:start job))
       "/PT" (:interval job) "S"))

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
     :name  (Long/parseLong name)
     :start (parse-file-time start)
     :end   (parse-file-time end)}))

(defn read-runs
  "Returns all runs from all nodes."
  [test]
  (->> (c/on-many
         (:nodes test)
         (->> (cu/ls-full slave-job-dir)
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
              ; (Chronos) won't schedule tasks concurrently, so we ensure they'll
              ; never overlap.
              interval    (+ 1
                             duration
                             epsilon
                             epsilon-forgiveness
                             (rand-int 30))]
        {:type   :invoke
         :f      :add-job
         :value  {:name     (swap! id inc)
                  :start    (time/plus (time/now) (time/seconds head-start))
                  :count    (inc (rand-int 99))
                  :duration duration
                  :epsilon  epsilon
                  :interval interval}})))))

(def noop
  "Does nothing."
  (reify Client
    (setup! [this test node]
      (info node "setting up client")
      this)
    (teardown! [this test])
    (invoke!   [this test op] (assoc op :type :ok))))

(defn simple-test
  [mesos-version]
  (assoc tests/noop-test
         :name      "aurora"
         :os        debian/os
         :db        (db mesos-version)
         :client    noop ;; (->Client nil)
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
         ;;             {:aurora (checker)
         ;;              :perf (checker/perf)})
))

