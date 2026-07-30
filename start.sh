#!/bin/bash
set -euo pipefail

if [ -f .env ]; then
  while IFS='=' read -r key value; do
    case "$key" in
      ''|'#'*)
        continue
        ;;
    esac

    export "$key=$value"
  done < .env
fi

APP_TIMEZONE="${APP_TIMEZONE:-Europe/Kyiv}"
export APP_TIMEZONE
export TZ="$APP_TIMEZONE"

java -XX:+UseSerialGC -Xss512k -Xms64m -Xmx1024m \
  -Duser.timezone="$APP_TIMEZONE" \
  -jar test-bot-project-1.0-SNAPSHOT.jar

sv restart vdown