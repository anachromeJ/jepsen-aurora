#!/bin/bash

NAME=$1
DURATION=$2
PARENT_JOB_DIR=/tmp/aurora-jobs/
SLAVE_JOB_DIR=/tmp/aurora-test/

mkdir -p $PARENT_JOB_DIR

TEMPJOB=$(mktemp -p $PARENT_JOB_DIR)
echo "MEW=\$(mktemp -p " $SLAVE_JOB_DIR "); " \
    "echo \"" $NAME "\" >> $MEW; " \
    "date -u -Ins >> $MEW; " \
    "sleep " $DURATION "; " \
    "date -u -Ins >> $MEW;" \
    > $TEMPJOB

TEMPCONFIG=$TEMPJOB.aurora
printf 
"$TEMPJOB
import hashlib
with open(pkg_path, 'rb') as f:
  pkg_checksum = hashlib.md5(f.read()).hexdigest()

# copy hello_world.py into the local sandbox
install = Process(
  name = 'fetch_package',
  cmdline = 'cp %s . && echo %s && chmod +x $TEMPJOB' % (pkg_path, pkg_checksum))

# run the script
$NAME = Process(
  name = '$NAME',
  cmdline = '$TEMPJOB')

# describe the task
$NAME_task = SequentialTask(
  processes = [install, $NAME],
  resources = Resources(cpu = 1, ram = 1*MB, disk=8*MB))

jobs = [
  Service(cluster = 'testcluster',
          environment = 'devel',
          role = 'www-data',
          name = $NAME,
          task = $NAME_task)
]" > $TEMPCONFIG

/jepsen/jepsen-aurora/aurora/aurora.pex job create testcluster/www-data/devel/simple-job $TEMPCONFIG
