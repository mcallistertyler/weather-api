package io.github.mcallistertyler.event.weather.api.domain;

import java.util.Objects;

public class Coordinates {
    Double lat;
    Double lon;

    public Coordinates(Double lat, Double lon) {
        this.lat = roundTwoDecimals(lat);
        this.lon = roundTwoDecimals(lon);
    }

    public Double getLat() {
        return lat;
    }

    public Double getLon() {
        return lon;
    }

    @Override
    public String toString() {
        return "Coordinates{" +
                "lat=" + lat.toString() + "," +
                "lon=" + lon.toString() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Coordinates oCoordinates = (Coordinates) o;
        return Objects.equals(this.lat, oCoordinates.lat) && Objects.equals(this.lon, oCoordinates.lon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lat, lon);
    }

    private Double roundTwoDecimals(double position) {
        position = Math.round(position * 100.0);
        position = position / 100.0;
        return position;
    }
}
