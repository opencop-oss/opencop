#!/bin/sh
# Copyright 2026 NexTech Solutions LLC
# SPDX-License-Identifier: Apache-2.0


set -eu

write_config() {
  port="$1"
  data_dir="/data/$port"
  mkdir -p "$data_dir"
  cat > "$data_dir/redis.conf" <<EOF
port $port
bind 0.0.0.0
protected-mode no
appendonly no
save ""
dir $data_dir
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000
cluster-announce-port $port
cluster-announce-bus-port $((port + 10000))
EOF
}

write_config 17100
write_config 17101
write_config 17102

redis-server /data/17100/redis.conf &
pid_17100=$!
redis-server /data/17101/redis.conf &
pid_17101=$!
redis-server /data/17102/redis.conf &
pid_17102=$!

terminate() {
  kill "$pid_17100" "$pid_17101" "$pid_17102" 2>/dev/null || true
  wait "$pid_17100" "$pid_17101" "$pid_17102" 2>/dev/null || true
}

trap terminate TERM INT

wait "$pid_17100" "$pid_17101" "$pid_17102"
