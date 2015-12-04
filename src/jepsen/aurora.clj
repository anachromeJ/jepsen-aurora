(ns jepsen.aurora
  "Sets up Aurora"
  (:require [jepsen [client :as client]
                    [db :as db]
                    [tests :as tests]
                    [control :as c :refer [|]]
                    [checker :as checker]
                    [generator :as gen]
                    [util :as util :refer [meh timeout]]
                    [zookeeper :as zk]])
)

(defn build-aurora
  ""
  []
  (c/exec "sudo apt-get update")
  (c/exec "sudo apt-get install build-essential")
  (c/exec "git clone http://git-wip-us.apache.org/repos/asf/aurora.git")
  (c/exec "cd aurora")
  (c/exec "./gradlew distZip"))

