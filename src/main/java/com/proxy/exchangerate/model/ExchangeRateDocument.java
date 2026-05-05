package com.proxy.exchangerate.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Document(indexName = "exchange-rates")
public class ExchangeRateDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String base;

    @Field(type = FieldType.Keyword, name = "timestamp")
    private String timestamp;

    @Field(type = FieldType.Object)
    private Map<String, Double> rates;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime indexedAt;

    @Field(type = FieldType.Keyword)
    private String source;

    public ExchangeRateDocument() {}

    public ExchangeRateDocument(String id, String base, String timestamp,
                                Map<String, Double> rates, LocalDateTime indexedAt, String source) {
        this.id = id;
        this.base = base;
        this.timestamp = timestamp;
        this.rates = rates;
        this.indexedAt = indexedAt;
        this.source = source;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBase() { return base; }
    public void setBase(String base) { this.base = base; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public Map<String, Double> getRates() { return rates; }
    public void setRates(Map<String, Double> rates) { this.rates = rates; }

    public LocalDateTime getIndexedAt() { return indexedAt; }
    public void setIndexedAt(LocalDateTime indexedAt) { this.indexedAt = indexedAt; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Double getRateFor(String currency) {
        if (rates == null || currency == null) return null;
        return rates.get(currency.toUpperCase());
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String base;
        private String timestamp;
        private Map<String, Double> rates;
        private LocalDateTime indexedAt;
        private String source;

        public Builder id(String id) { this.id = id; return this; }
        public Builder base(String base) { this.base = base; return this; }
        public Builder timestamp(String timestamp) { this.timestamp = timestamp; return this; }
        public Builder rates(Map<String, Double> rates) { this.rates = rates; return this; }
        public Builder indexedAt(LocalDateTime indexedAt) { this.indexedAt = indexedAt; return this; }
        public Builder source(String source) { this.source = source; return this; }

        public ExchangeRateDocument build() {
            return new ExchangeRateDocument(id, base, timestamp, rates, indexedAt, source);
        }
    }

    @Override
    public String toString() {
        return "ExchangeRateDocument{id='" + id + "', base='" + base + "', timestamp='" + timestamp + "'}";
    }
}
