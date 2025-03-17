#!/bin/bash

now=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
if [[ "$OSTYPE" == "darwin"* ]]; then
    future_date=$(date -v+3d -u +"%Y-%m-%dT%H:%M:%SZ")
else
    # Linux
    future_date=$(date -u -d "+3 days" +"%Y-%m-%dT%H:%M:%SZ")
fi


echo "/forecast request"
curl -X GET "http://localhost:8080/forecast?lat=59.913&lon=10.752&startDateTime="$now"&endDateTime="$future_date"" \
  -H "Accept: application/json" | jq
echo "---------"
echo "/forecast/extended request"
curl -X GET "http://localhost:8080/forecast/extended?lat=59.913&lon=10.752&startDateTime="$now"&endDateTime="$future_date"" \
  -H "Accept: application/json" | jq
