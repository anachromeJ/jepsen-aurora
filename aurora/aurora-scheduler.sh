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
AURORA_FLAGS=(
  -cluster_name=jepsen-aurora-testcluster
  -http_port=8081
  -native_log_quorum_size=1
  -zk_endpoints=localhost:2181
  -mesos_master_address=zk://localhost:2181/mesos/master
  -serverset_path=/aurora/scheduler
  -native_log_zk_group_path=/aurora/replicated-log
  -native_log_file_path="$AURORA_SCHEDULER_HOME/db"
  -backup_dir="$AURORA_SCHEDULER_HOME/backups"

  # TODO(Kevin Sweeney): Point these to real URLs.
  -thermos_executor_path=/dev/null
  # -gc_executor_path=/dev/null

  -vlog=INFO
  -logtostderr

  -allowed_container_types=MESOS,DOCKER
  -http_authentication_mechanism=BASIC
  -use_beta_db_task_store=true
  -shiro_ini_path="$AURORA_SCHEDULER_HOME/etc/shiro.example.ini"
  -enable_h2_console=true
  -receive_revocable_resources=true
)

# Environment variables control the behavior of the Mesos scheduler driver (libmesos).
export GLOG_v=0
export LIBPROCESS_PORT=8083

JAVA_OPTS="${JAVA_OPTS[*]}" exec "$AURORA_SCHEDULER_HOME/bin/aurora-scheduler" "${AURORA_FLAGS[@]}"

