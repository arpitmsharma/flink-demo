package com.github.arpitmsharma;


import lombok.Data;

public class TemperatureEvent {
    private Integer devId;
    private Integer value;

    public Integer getDevId() {
        return devId;
    }

    public void setDevId(Integer devId) {
        this.devId = devId;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}