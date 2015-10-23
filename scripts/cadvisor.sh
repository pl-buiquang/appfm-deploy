#!/bin/bash

sudo docker run \
    --volume=/:/rootfs:ro \
      --volume=/var/run:/var/run:rw \
        --volume=/sys:/sys:ro \
          --volume=/var/lib/docker/:/var/lib/docker:ro \
            --publish=8082:8080 \
              --detach=true \
                --name=cadvisor \
                  google/cadvisor:latest \
                  -storage_driver_db=cadvisor -storage_driver_host=172.17.42.1:8086

sudo docker run -d -p 8081:3000 -e INFLUXDB_HOST=172.17.42.1 -e INFLUXDB_PORT=8086 -e INFLUXDB_NAME=cadvisor -e INFLUXDB_USER=root -e INFLUXDB_PASS=root --name grafana grafana/grafana
