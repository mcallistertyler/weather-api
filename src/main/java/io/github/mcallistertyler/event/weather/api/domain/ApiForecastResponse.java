package io.github.mcallistertyler.event.weather.api.domain;

import java.util.List;

public record ApiForecastResponse(List<WeatherData> weatherData, String message, int code) {
}
