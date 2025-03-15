package io.github.mcallistertyler.event.weather.api.domain;

import java.util.List;

public record ApiForecastResponse(List<MetForcecastResponse.TimeSeries> timeSeries) {
}
