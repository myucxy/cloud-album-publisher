package com.cloudalbum.publisher.android.data.model;

public class ApiResult<T> {
    private int code;
    private String message;
    private T data;
    private long timestamp;

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
