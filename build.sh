#!/bin/bash

docker compose down && docker compose up -d --build && docker logs -f wexow-server
