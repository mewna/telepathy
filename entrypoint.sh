#!/usr/bin/env bash

nginx & (sleep 5 && /usr/bin/java -Xms128M -Xmx256M -jar /app/telepathy.jar)