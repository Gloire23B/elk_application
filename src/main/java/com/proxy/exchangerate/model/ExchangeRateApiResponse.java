package com.proxy.exchangerate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExchangeRateApiResponse {

    @JsonProperty("result")
    private String result;

    @JsonProperty("base_code")
    private String baseCode;

    @JsonProperty("base")
    private String base;

    @JsonProperty("date")
    private String date;

    @JsonProperty("time_last_update_utc")
    private String timeLastUpdateUtc;

    @JsonProperty("time_next_update_utc")
    private String timeNextUpdateUtc;

    @JsonProperty("rates")
    private Map<String, Double> rates;

    public ExchangeRateApiResponse() {}

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getBaseCode() { return baseCode; }
    public void setBaseCode(String baseCode) { this.baseCode = baseCode; }

    public String getBase() { return base; }
    public void setBase(String base) { this.base = base; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTimeLastUpdateUtc() { return timeLastUpdateUtc; }
    public void setTimeLastUpdateUtc(String v) { this.timeLastUpdateUtc = v; }

    public String getTimeNextUpdateUtc() { return timeNextUpdateUtc; }
    public void setTimeNextUpdateUtc(String v) { this.timeNextUpdateUtc = v; }

    public Map<String, Double> getRates() { return rates; }
    public void setRates(Map<String, Double> rates) { this.rates = rates; }

    public String resolveBase() {
        if (baseCode != null && !baseCode.isBlank()) return baseCode;
        if (base != null && !base.isBlank()) return base;
        return "USD";
    }

    public boolean isValid() {
        return rates != null && !rates.isEmpty() && resolveBase() != null;
    }
}
