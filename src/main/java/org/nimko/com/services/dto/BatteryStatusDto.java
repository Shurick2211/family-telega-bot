package org.nimko.com.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BatteryStatusDto(
    boolean present,
    String technology,
    String health,
    String plugged,
    String status,
    double temperature,
    int voltage,
    int current,
    int percentage,
    int level,
    int scale,
    @JsonProperty("charge_counter") int chargeCounter,
    int cycle
) {
}
