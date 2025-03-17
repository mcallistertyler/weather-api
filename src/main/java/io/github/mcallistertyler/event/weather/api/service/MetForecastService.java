package io.github.mcallistertyler.event.weather.api.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.github.mcallistertyler.event.weather.api.domain.Coordinates;
import io.github.mcallistertyler.event.weather.api.domain.MetForecastResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MetForecastService {

    private static final Logger log = LoggerFactory.getLogger(MetForecastService.class);

    @Value("${api.metno.base-url}")
    private String baseUrl;

    @Value("${api.metno.user-agent}")
    private String userAgent;

    private final OkHttpClient httpClient;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final int MAX_CACHE_SIZE = 1000;
    private final Duration CACHE_EXPIRATION = Duration.ofHours(2);

    private final LoadingCache<Coordinates, MetForecastResponse> forecastCache = CacheBuilder.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .recordStats()
            .expireAfterWrite(CACHE_EXPIRATION)
            .build(new CacheLoader<>() {
                @NotNull
                @Override
                public MetForecastResponse load(@NotNull Coordinates coordinates) throws IOException {
                    try {
                        Optional<MetForecastResponse> forecastResponse = fetchMetForecastFromApi(coordinates, null);
                        if (forecastResponse.isPresent()) {
                            return forecastResponse.get();
                        } else {
                            throw new IllegalStateException("No response returned from met api for coordinates " + coordinates);
                        }
                    } catch (IOException e) {
                        log.error("Exception occurred when trying to get forecast response for coordinates: {}", coordinates, e);
                        throw new IOException("Failed to retrieve forecast response from coordinates", e);
                    }
                }
            });

    public MetForecastService(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Optional<MetForecastResponse> getForecast(Coordinates coordinates) {
        try {
            MetForecastResponse cachedForecast = forecastCache.getIfPresent(coordinates);
            if (cachedForecast != null) {
               if (cachedForecast.isDataFresh()) {
                   log.info("Returning cached response since it has not yet expired.");
                   return Optional.of(cachedForecast);
               }

               log.info("Forecast has expired. New forecast will be fetched");

               String lastModified = cachedForecast.lastModifiedHeader() != null ? cachedForecast.lastModifiedHeader() : null;
               Optional<MetForecastResponse> refreshedForecastResponse = fetchMetForecastFromApi(coordinates, lastModified);
               if (refreshedForecastResponse.isEmpty()) {
                   return Optional.of(cachedForecast);
               }
               forecastCache.put(coordinates, refreshedForecastResponse.get());
               return refreshedForecastResponse;
            }
            return Optional.of(forecastCache.get(coordinates));
        } catch (Exception e) {
            log.error("Failed to retrieve forecast. Returning possible cached value", e);
            return Optional.ofNullable(forecastCache.getIfPresent(coordinates));
        }
    }


    public Optional<MetForecastResponse> fetchMetForecastFromApi(Coordinates coordinates, String ifModifiedHeader) throws IOException {
        HttpUrl httpUrl = new HttpUrl.Builder()
                .scheme("https")
                .host(baseUrl)
                .addPathSegments("weatherapi/locationforecast/2.0/compact")
                .addQueryParameter("lat", String.valueOf(coordinates.getLat()))
                .addQueryParameter("lon", String.valueOf(coordinates.getLon()))
                .build();

        final Request.Builder request = new Request.Builder()
                .url(httpUrl)
                .addHeader("User-Agent", userAgent)
                .get();

        if (ifModifiedHeader != null) {
            request.addHeader("If-Modified-Since", ifModifiedHeader);
        }
        try (Response response = httpClient.newCall(request.build()).execute()) {
            switch (response.code()) {
                case 304:
                    log.info("304 received for forecast. Re-use previous forecast");
                    return Optional.empty();
                case 429:
                    log.error("Service has been marked for throttling. Consider reducing number of requests of increasing cache expiry.");
                    return Optional.empty();
                case 203:
                    log.warn("Met api has been marked as deprecated or is in beta phase. Consider consulting api.met.no documentation");
                case 200:
                    ResponseBody body = response.body();
                    String lastModifiedHeader = response.header("Last-Modified", null);
                    String expiresHeader = response.header("Expires", null);
                    if (body != null) {
                        String json = body.string();
                        JsonNode jsonNode = objectMapper.readTree(json);
                        return MetForecastResponse.parseMetResponse(jsonNode, lastModifiedHeader, expiresHeader);
                    }
                    break;
                default:
                    log.warn("Unexpected response code: {}", response.code());
            }
            return Optional.empty();
        } catch (IOException e) {
            log.error("Error when calling met weather API", e);
            throw new IOException("Error when calling met weather API", e);
        }
    }

}
