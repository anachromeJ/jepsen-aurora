#!/bin/bash

# terminate all child containers and then redo jepsen setup

for i in `seq 1 5`;
do
    docker stop n${i}
    docker rm -v n${i}
done

source ~/.bashrc
