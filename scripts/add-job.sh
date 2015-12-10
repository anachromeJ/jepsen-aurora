#!/bin/bash

NAME=job${1}
TASKNAME=${NAME}_task
DURATION=$2
COUNT=$3
JOB_SCRIPT_DIR=/tmp/aurora-jobs/
JOB_RESULT_DIR=/tmp/aurora-test/
AURORA_CLIENT=/jepsen/jepsen-aurora/resources/client.pex

mkdir -p $JOB_SCRIPT_DIR
TEMPJOB=$(mktemp -p $JOB_SCRIPT_DIR)
echo "MEW=\$(mktemp -p " $JOB_RESULT_DIR "); " \
    "echo \"" $NAME "\" >> \$MEW; " \
    "date -u -Ins >> \$MEW; " \
    "sleep " $DURATION "; " \
    "date -u -Ins >> \$MEW;" \
    > $TEMPJOB

# join arguments with a separator
function join { local IFS="$1"; shift; echo "$*"; }

ALLTASKS=$(join ", " $(for (( i=0; i < $COUNT; i++)); do printf $NAME; printf ' '; done))

TEMPCONFIG=$TEMPJOB.aurora
echo "pkg_path = '$TEMPJOB'
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
  cmdline = 'bash $TEMPJOB')

# describe the task
$TASKNAME = SequentialTask(
  processes = [install, $ALLTASKS],
  resources = Resources(cpu = 1, ram = 1*MB, disk=8*MB))

jobs = [
  Service(cluster = 'testcluster',
          environment = 'devel',
          role = 'www-data',
          name = '$NAME',
          task = $TASKNAME)
]" > $TEMPCONFIG

$AURORA_CLIENT job create testcluster/www-data/devel/$NAME $TEMPCONFIG
