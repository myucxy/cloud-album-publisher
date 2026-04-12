package com.cloudalbum.publisher.common.exception;

import com.cloudalbum.publisher.common.enums.ResultCode;

public class AuthException extends BaseException {

    public AuthException(ResultCode resultCode) {
        super(resultCode);
    }

    public AuthException(ResultCode resultCode, String message) {
        super(resultCode, message);
    }
}
