#!/bin/bash
set -e
cd "$(dirname "$0")"
docker build -t bitclicker-web .
docker rm -f bitclicker-web-dev 2>/dev/null || true
docker run -d --name bitclicker-web-dev -p 4003:4003 -e BASE_PATH="" bitclicker-web
echo "Running at http://localhost:4003"
