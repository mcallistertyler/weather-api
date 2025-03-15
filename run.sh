#/bin/bash

./gradlew clean build -x test
java -jar build/libs/event-weather-api-0.0.1-SNAPSHOT.jar
