#!/bin/sh
set -e
export CONFIG_SERVER_PASSWORD="$(cat /run/secrets/configserver_password)"
exec java -jar app.jar
