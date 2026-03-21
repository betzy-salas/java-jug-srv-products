package com.sdd.products.dto.request;

import jakarta.validation.constraints.NotNull;

public class StockAdjustmentRequest {

    @NotNull
    private Integer delta;

    public Integer getDelta() {
        return delta;
    }

    public void setDelta(Integer delta) {
        this.delta = delta;
    }
}
