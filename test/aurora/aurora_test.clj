(ns jepsen.aurora-test
  (:require [clojure.test :refer :all]
            [jepsen.core :refer [run!]]
            [jepsen.aurora :refer :all]))

(deftest a-test
  (testing "always succeed"
    (is (= 1 1))))
