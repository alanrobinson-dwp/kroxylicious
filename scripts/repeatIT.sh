#!/usr/bin/env bash
#
# Copyright Kroxylicious Authors.
#
# Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
#

targetTest=${1:-ExpositionIT}

logFile=/tmp/mvn-${targetTest}-$(date +"%d-%m-%Y_%H:%M").log

mvn clean install -DskipTests=true

for COUNT in {1..10} ; do
    echo "=============== RUN ${COUNT} STARTING =============== " | tee -a "${logFile}"
    mvn verify -Dit.test="${targetTest}" -pl :integrationtests >> "${logFile}"
    EC=$?
    echo "=============== RUN ${COUNT} FINISHED =============== " | tee -a "${logFile}"
    if [[ ${EC} -ne 0 ]]; then
      exit $EC
    fi
done