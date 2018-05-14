#!/bin/bash

ETCD_HOST=$(ip addr show docker0 | grep 'inet\b' | awk '{print $2}' | cut -d '/' -f 1)
ETCD_PORT=2379
ETCD_URL=http://$ETCD_HOST:$ETCD_PORT

echo ETCD_URL = $ETCD_URL

if [[ "$1" == "consumer" ]]; then
  echo "Starting consumer agent..."
  java -jar \
       -Xms1536M \
       -Xmx1536M \
       -Xss256k \
       -XX:+UseParallelOldGC \
       -Dtype=consumer \
       -Dserver.port=20000\
       -Detcd.url=$ETCD_URL \
       -Dlogs.dir=/root/logs \
       -Dnetty.server=8870 \
       -Dio.netty.leakDetectionLevel=DISABLED \
       /root/dists/mesh-agent.jar
elif [[ "$1" == "provider-small" ]]; then
  echo "Starting small provider agent..."
  java -jar \
       -Xms512M \
       -Xmx512M \
       -Xss256k \
       -XX:+UseParallelOldGC \
       -Dtype=provider \
       -Dserver.port=30000\
       -Ddubbo.protocol.port=20889 \
       -Detcd.url=$ETCD_URL \
       -Dlogs.dir=/root/logs \
       -Dnetty.server=8871 \
       -Ddubbo.application.qos.enable=false \
       -Dio.netty.leakDetectionLevel=DISABLED \
       /root/dists/mesh-agent.jar
elif [[ "$1" == "provider-medium" ]]; then
  echo "Starting medium provider agent..."
  java -jar \
       -Xms1536M \
       -Xmx1536M \
       -Xss256k \
       -XX:+UseParallelOldGC \
       -Dtype=provider \
       -Dserver.port=30001\
       -Ddubbo.protocol.port=20890 \
       -Detcd.url=$ETCD_URL \
       -Dlogs.dir=/root/logs \
       -Dnetty.server=8872 \
       -Ddubbo.application.qos.enable=false \
       -Dio.netty.leakDetectionLevel=DISABLED \
       /root/dists/mesh-agent.jar
elif [[ "$1" == "provider-large" ]]; then
  echo "Starting large provider agent..."
  java -jar \
       -Xms2560M \
       -Xmx2560M \
       -Xss256k \
       -XX:+UseParallelOldGC \
       -Dtype=provider \
       -Dserver.port=30002\
       -Ddubbo.protocol.port=20891 \
       -Detcd.url=$ETCD_URL \
       -Dlogs.dir=/root/logs \
       -Dnetty.server=8873 \
       -Ddubbo.application.qos.enable=false \
       -Dio.netty.leakDetectionLevel=DISABLED \
       /root/dists/mesh-agent.jar
else
  echo "Unrecognized arguments, exit."
  exit 1
fi
