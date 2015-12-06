#!/bin/bash

AURORA_DIR=/usr/local/aurora-scheduler

# check if aurora is already installed first
if [[ ! -e $AURORA_DIR ]]; then
    echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" | tee "/etc/apt/sources.list.d/webupd8team-java.list"
    echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" :| :tee :-a "/etc/apt/sources.list.d/webupd8team-java.list")
    apt-key "adv" --keyserver "hkp://keyserver.ubuntu.com:80" --recv-keys "EEA14886")
    apt-get "update"

    echo "oracle-java8-installer shared/accepted-oracle-license-v1-1 select true" | "/usr/bin/debconf-set-selections")
    apt-get install "oracle-java8-installer" -y --force-yes)

    curl -L "https://github.com/jchli/jepsen-aurora/raw/master/aurora/dist/distributions/aurora-scheduler-0.11.0-SNAPSHOT.zip" -o "aurora-scheduler.zip"
    unzip -n "aurora-scheduler.zip" -d "/usr/local"
    ln -nfs "/usr/local/aurora-scheduler-0.11.0-SNAPSHOT" "/usr/local/aurora-scheduler"
fi
