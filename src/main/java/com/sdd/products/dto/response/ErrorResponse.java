package com.sdd.products.dto.response;

import java.util.List;

public class ErrorResponse {

    private String error;
    private String message;
    private List<String> fields;

    public ErrorResponse(String error, String message, List<String> fields) {
        this.error = error;
        this.message = message;
        this.fields = fields != null ? fields : List.of();
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields != null ? fields : List.of();
    }
}
