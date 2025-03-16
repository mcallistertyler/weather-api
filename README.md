
# Weather API

This is the source code for the weather API. It serves as a basic implementation of an example of a microservice that allows a user to send information about upcoming events (latitude, longitude, start date and end date) to retrieve either a weather response for the next hour or a forecast for the entire duration of the event.

The service was written in Java 21 and is built via gradle. A gradle wrapper is included with the code. Build, run and test shell scripts are also included to simplify the process.

## List of improvements

Some improvements could be made both technically and conceptually, such as:

- The OkHttpClient requests should probably be asynchronous to allow for more concurrent connections and support heavy load.
- The Guava cache runs in-memory and would be empty upon restarting the service. A cache with the persistence functionally, such as Redis, would most likely be a better idea.
- The Exceptions can be a bit messy or vague, custom exceptions with more detailed descriptions would improve clarity.
- Metrics and monitoring would be useful to add if we want to look closely at cache hit rate or API response times. These can be collected via Prometheus and viewed via Grafana or Datadog.
- There are tests missing for the API itself. Verifying how responses look can be useful.
- Load testing would be beneficial to measure how well the service deals with high traffic.

## MET Api
There are some considerations to keep in mind while using the MET Weather API.

The API has bandwith requirements:
> "20 requests/second per application (total, not per client) requires special agreement"

So in addition to adding monitoring to all API calls a threshold value could be used to prevent further requests or rate limiting methods could be used.