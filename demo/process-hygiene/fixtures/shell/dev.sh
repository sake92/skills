#!/bin/bash

API_TOKEN=$1

echo "starting dev servers"
npm run dev &
python -m http.server 9000 &

sleep 5

if curl -s localhost:3000/health; then
  echo "api is up"
else
  echo "api failed to start"
fi

curl -s -H "Authorization: Bearer $API_TOKEN" localhost:3000/orders > orders.json
echo "fetched $(wc -l < orders.json) lines"

echo "done"
