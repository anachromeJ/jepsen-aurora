#!/bin/bash

AURORA_DIR=/usr/local/aurora-scheduler
AURORA_SNAPSHOT=aurora-scheduler-0.11.0-SNAPSHOT
AURORA_ZIP=$AURORA_SNAPSHOT.zip
AURORA_ZIP_LINK=https://github.com/jchli/jepsen-aurora/raw/master/resources/$AURORA_ZIP

readonly MESOS_VERSION=0.24.1

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
    ln -nfs "/usr/local/$AURORA_SNAPSHOT" $AURORA_DIR
    export PATH=$AURORA_DIR/bin:$PATH

    # Add Log output parameter
    mesos-log initialize --path=$AURORA_DIR/db
else
    echo "aurora already installed, exiting..."
fi

# install mesos egg and build thermos
if [[ ! -e "/aurora" ]]; then
    cd /
    apt-get update
    apt-get -y install gcc bison curl git libapr1-dev libcurl4-nss-dev \
        libsasl2-dev libsvn-dev python-dev zookeeperd
    git clone https://github.com/apache/aurora.git
    pushd aurora
    mkdir -p third_party
    pushd third_party
    wget -c https://svn.apache.org/repos/asf/aurora/3rdparty/ubuntu/trusty64/python/mesos.native-${MESOS_VERSION}-py2.7-linux-x86_64.egg
    popd

    $deb=mesos_${MESOS_VERSION}-0.2.35.ubuntu1204_amd64.deb
    wget -c http://downloads.mesosphere.io/master/ubuntu/12.04/$deb
    dpkg --install $deb

    # build thermos executor and runner
    ./pants binary src/main/python/apache/aurora/executor:thermos_executor
    ./pants binary src/main/python/apache/thermos/runner:thermos_runner
    
    # package runner within executor
    build-support/embed_runner_in_executor.py

    chmod +x /aurora/dist/thermos_executor.pex
    popd
else
    echo "thermos already installed, exiting..."
fi
