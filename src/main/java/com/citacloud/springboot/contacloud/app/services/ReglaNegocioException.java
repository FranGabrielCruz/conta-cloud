package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.errors.ErrorCode;

public class ReglaNegocioException extends RuntimeException {
    private final ErrorCode code;
    public ReglaNegocioException(String message) { this(ErrorCode.BUSINESS_RULE_ERROR,message,null); }
    public ReglaNegocioException(String message, Throwable cause) { this(ErrorCode.BUSINESS_RULE_ERROR,message,cause); }
    public ReglaNegocioException(ErrorCode code,String message) { this(code,message,null); }
    public ReglaNegocioException(ErrorCode code,String message,Throwable cause) { super(message,cause);this.code=code==null?ErrorCode.BUSINESS_RULE_ERROR:code; }
    public ErrorCode getCode(){return code;}
}
