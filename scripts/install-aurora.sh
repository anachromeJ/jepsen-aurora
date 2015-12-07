#!/bin/bash

AURORA_DIR=/usr/local/aurora-scheduler
AURORA_ZIP=aurora-scheduler-0.11.0-SNAPSHOT.zip
AURORA_ZIP_LINK=https://github.com/jchli/jepsen-aurora/raw/master/resources/$AURORA_ZIP

# check if aurora is already installed first
if [[ ! -e $AURORA_DIR ]]; then
    echo "installing aurora..."
    echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" | tee "/etc/apt/sources.list.d/webupd8team-java.list"
    echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" | tee -a "/etc/apt/sources.list.d/webupd8team-java.list"
    apt-key "adv" --keyserver "hkp://keyserver.ubuntu.com:80" --recv-keys "EEA14886"
    apt-get update

    echo "oracle-java8-installer shared/accepted-oracle-license-v1-1 select true" | "/usr/bin/debconf-set-selections"
    apt-get install "oracle-java8-installer" -y --force-yes

    curl -L $AURORA_ZIP_LINK -o $AURORA_ZIP
    unzip -n $AURORA_ZIP -d "/usr/local"
    ln -nfs "/usr/local/$AURORA_ZIP" $AURORA_DIR
    export PATH=$AURORA_DIR/bin:$PATH

    # Add Log output parameter
    mesos-log initialize --path=$AURORA_DIR/db
    echo "done"
else
    echo "aurora already installed, aborting..."
fi
