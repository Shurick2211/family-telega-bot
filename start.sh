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

java -jar test-bot-project-1.0-SNAPSHOT.jar