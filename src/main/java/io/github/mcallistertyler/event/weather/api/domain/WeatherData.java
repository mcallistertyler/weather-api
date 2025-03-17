package io.github.mcallistertyler.event.weather.api.domain;

import java.time.Instant;

public record WeatherData(Instant time, Double windSpeed, Double airTemperature) {
}
