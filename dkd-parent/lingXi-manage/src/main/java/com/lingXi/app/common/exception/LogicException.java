package com.lingXi.app.common.exception;

/**
 * 逻辑异常
 */
public class LogicException extends RuntimeException{
    public LogicException(String errorMsg){
        super(errorMsg);
    }
}