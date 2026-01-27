package com.doubao.voice.exception;

/**
 * 豆包语音服务业务异常
 */
public class DoubaoException extends RuntimeException {

    private String errorCode;

    public DoubaoException(String message) {
        super(message);
    }

    public DoubaoException(String message, Throwable cause) {
        super(message, cause);
    }

    public DoubaoException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DoubaoException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
