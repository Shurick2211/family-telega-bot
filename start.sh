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

java -XX:+UseSerialGC -Xss512k -Xms64m -Xmx1024m -jar test-bot-project-1.0-SNAPSHOT.jar

sv restart vdown