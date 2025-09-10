package org.example.springboot.enumClass;

import io.swagger.v3.oas.annotations.media.Schema;

public enum AccountStatus {
    @Schema(description = "") DISABLED(0),
    @Schema(description = "") NORMAL(1);


    private final Integer value;

    AccountStatus(Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}