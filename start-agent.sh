#!/bin/bash

ETCD_HOST=etcd
ETCD_PORT=2379
ETCD_URL=http://$ETCD_HOST:$ETCD_PORT

echo ETCD_URL = $ETCD_URL

if [[ "$1" == "consumer" ]]; then
  echo "Starting consumer agent..."
  java -jar \
       -Xms1536M \
       -Xmx1536M \
       -XX:MaxDirectMemorySize=2G \
       -Xss256k \
       -XX:+UseParallelOldGC \
       -Dtype=consumer \
       -Dserver.port=20000\
       -Detcd.url=$ETCD_URL \
       -Dlogs.dir=/root/logs \
       -Dnetty.server=8870 \
       -Dio.netty.leakDetectionLevel=DISABLED \
       -Dio.netty.allocator.numHeapArenas=16 \
       -Dio.netty.allocator.numDirectArenas=16 \
       -Dio.netty.buffer.bytebuf.checkAccessible=false \
       -Dio.netty.onKeySetOptimization=true \
       /root/dists/mesh-agent.jar
elif [[ "$1" == "provider-small" ]]; then
  echo "Starting small provider agent..."
  java -jar \
       -Xms512M \
       -Xmx512M \
       -Xss256k \
       -XX:MaxDirectMemorySize=2G \
       -XX:+UseParallelOldGC \
       -Dtype=provider \
       -Dserver.port=30000\
       -Ddubbo.protocol.port=20880 \
       -Detcd.url=$ETCD_URL \
       -Dlogs.dir=/root/logs \
       -Ddubbo.application.qos.enable=false \
       -Dio.netty.leakDetectionLevel=DISABLED \
       -Dio.netty.allocator.numHeapArenas=16 \
       -Dio.netty.allocator.numDirectArenas=16 \
       -Dio.netty.buffer.bytebuf.checkAccessible=false \
       /root/dists/mesh-agent.jar
elif [[ "$1" == "provider-medium" ]]; then
  echo "Starting medium provider agent..."
  java -jar \
       -Xms1536M \
       -Xmx1536M \
       -Xss256k \
       -XX:+UseParallelOldGC \
       -XX:MaxDirectMemorySize=2G \
       -Dtype=provider \
       -Dserver.port=30001\
       -Ddubbo.protocol.port=20880 \
       -Detcd.url=$ETCD_URL \
       -Dlogs.dir=/root/logs \
       -Ddubbo.application.qos.enable=false \
       -Dio.netty.leakDetectionLevel=DISABLED \
       -Dio.netty.allocator.numHeapArenas=16 \
       -Dio.netty.allocator.numDirectArenas=16 \
       -Dio.netty.buffer.bytebuf.checkAccessible=false \
       /root/dists/mesh-agent.jar
elif [[ "$1" == "provider-large" ]]; then
  echo "Starting large provider agent..."
  java -jar \
       -Xms2560M \
       -Xmx2560M \
       -Xss256k \
       -XX:MaxDirectMemorySize=2G \
       -XX:+UseParallelOldGC \
       -Dtype=provider \
       -Dserver.port=30002\
       -Ddubbo.protocol.port=20880 \
       -Detcd.url=$ETCD_URL \
       -Dlogs.dir=/root/logs \
       -Ddubbo.application.qos.enable=false \
       -Dio.netty.leakDetectionLevel=DISABLED \
       -Dio.netty.allocator.numHeapArenas=16 \
       -Dio.netty.allocator.numDirectArenas=16 \
       -Dio.netty.buffer.bytebuf.checkAccessible=false \
       /root/dists/mesh-agent.jar
else
  echo "Unrecognized arguments, exit."
  exit 1
fi
