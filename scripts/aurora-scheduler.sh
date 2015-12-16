#!/usr/bin/env bash

# Location where aurora-scheduler.zip was unpacked.
AURORA_SCHEDULER_HOME=/usr/local/aurora-scheduler

# Flags that control the behavior of the JVM.
JAVA_OPTS=(
  -server
  -Xmx2g
  -Xms2g

  # Location of libmesos-0.18.0.so / libmesos-0.18.0.dylib
  -Djava.library.path=/usr/local/lib
)


# Flags control the behavior of the Aurora scheduler.
# For a full list of available flags, run bin/aurora-scheduler -help
ZK_QUORUM=10.0.0.2:2181,10.0.0.3:2181,10.0.0.4:2181,10.0.0.5:2181,10.0.0.6:2181
AURORA_FLAGS=(
  -cluster_name=testcluster
  -http_port=8081
  -native_log_quorum_size=1
  -zk_endpoints=${ZK_QUORUM}
  -mesos_master_address=zk://${ZK_QUORUM}/mesos
  -serverset_path=/aurora/scheduler
  -native_log_zk_group_path=/aurora/replicated-log
  -native_log_file_path="$AURORA_SCHEDULER_HOME/db"
  -backup_dir="$AURORA_SCHEDULER_HOME/backups"

  -thermos_executor_path=/aurora/dist/thermos_executor.pex
  -thermos_executor_flags="--announcer-enable --announcer-ensemble localhost:2181"
  # -gc_executor_path=/dev/null

  -vlog=INFO
  -logtostderr

  -allowed_container_types=MESOS,DOCKER
  -http_authentication_mechanism=NONE
  -use_beta_db_task_store=true
  -shiro_ini_path="$AURORA_SCHEDULER_HOME/etc/shiro.example.ini"
  -enable_h2_console=true
  -receive_revocable_resources=true
)

# Environment variables control the behavior of the Mesos scheduler driver (libmesos).
export GLOG_v=0
export LIBPROCESS_PORT=8083

# clean up log files
rm -f $AURORA_SCHEDULER_HOME/db/*
mesos-log initialize --path=$AURORA_SCHEDULER_HOME/db

# start scheduler
JAVA_OPTS="${JAVA_OPTS[*]}" exec "$AURORA_SCHEDULER_HOME/bin/aurora-scheduler" "${AURORA_FLAGS[@]}" > /dev/null &

