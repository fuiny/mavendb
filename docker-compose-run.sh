#!/bin/bash

# Clean Docker containers, if exists
sudo docker compose down --rmi local
sudo rm -rf mysql-data/
sudo rm -rf mavendb-log
sudo rm -rf mavendb-var

# Create Docker containers
sudo docker compose up -d

echo  "Waiting for mysql to be ready"
sleep 30

# Run
sudo docker compose run  mavendb

