
# Weather API

This is the source code for the weather API. It serves as a basic implementation of an example of a microservice that allows a user to send information about upcoming events (latitude, longitude, start date and end date) to retrieve either a weather response for the next hour or a forecast for the entire duration of the event.

The service was written in Java 21 and is built via Gradle 4.10.2. A gradle wrapper is included with the code. Build, run and test shell scripts are also included to simplify the process.

## List of improvements and next steps

Some improvements could be made both technically and conceptually, such as:

- The OkHttpClient requests should probably be asynchronous to allow for more concurrent connections and support heavy load.
- The Guava cache runs in-memory and would be empty upon restarting the service. A cache with the persistence functionally, such as Redis, would most likely be a better idea.
- The Exceptions can be a bit messy or vague, custom exceptions with more detailed descriptions would improve clarity.
- Metrics and monitoring would be useful to add if we want to look closely at cache hit rate or API response times. These can be collected via Prometheus and viewed via Grafana or Datadog.
- There are tests missing for the API itself. Verifying how responses look can be useful.
- Load testing would be beneficial to measure how well the service deals with high traffic.

## MET API Considerations

There are some considerations to keep in mind while using the MET Weather API.

The API has bandwith requirements:
> "20 requests/second per application (total, not per client) requires special agreement"

So in addition to adding monitoring to all API calls a threshold value could be used to prevent further requests or rate limiting methods could be used.

Further considerations are that altitude data is missing, which they state can be recommended for more accurate temperature predictions:
> Optional but recommended for precise temperature values. When missing the internal topography model is used for temperature correction, which is rather course and may be incorrect in hilly terrain.

## Endpoints

Returns the weather forecast for the nearest available time after the current moment.

#### Endpoints

```
GET /forecast
```
```
GET /forecast/extended
```

Both endpoints return the same json response however `/forecast` will only return the next hour while `/forecast/extended` will return a full forecast
between the `startDateTime` and `endDateTime` provided.

#### Query Parameters

| Parameter     | Type    | Description                                                  |
|---------------|---------|--------------------------------------------------------------|
| lat           | double  | Latitude coordinate                                          |
| lon           | double  | Longitude coordinate                                          |
| startDateTime | Instant | Start date and time (ISO 8601 format)                        |
| endDateTime   | Instant | End date and time (ISO 8601 format)                          |

#### Response

```json
{
  "weatherData": [
    {
      "time": "2025-03-17T15:00:00Z",
      "windSpeed": 1.9,
      "airTemperature": 6.0
    }
  ],
  "message": "OK",
  "statusCode": 200
}
```

#### Status Codes

- `200 OK`: Request successful
- `204 No Content`: No forecast data available for the requested parameters
- `400 Bad Request`: Invalid request parameters (including when startDateTime is not within the next 7 days)
- `404 Not Found`: No forecast found for given coordinates

## Local Testing
Java 21 and Gradle 4.10.2 are required to build and run the code. Convenience build and run scripts are included.
A curl script (`./curl.sh`) is also included to show two example requests. It includes clean JSON parsing via [jq](https://github.com/jqlang/jq) so you'll
have to make sure that's installed to run the script.