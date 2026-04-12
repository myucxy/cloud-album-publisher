package com.cloudalbum.publisher.common.exception;

import com.cloudalbum.publisher.common.enums.ResultCode;

public class BusinessException extends BaseException {

    public BusinessException(ResultCode resultCode) {
        super(resultCode);
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(resultCode, message);
    }
}
