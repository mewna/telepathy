#!/usr/bin/env bash

rc-service nginx restart && /usr/bin/java -Xms128M -Xmx256M -jar /app/telepathy.jar