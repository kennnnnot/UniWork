package com.idongxia.uniwork;

/**
 * UniWork 对外统一抛出的运行时异常，调用方无需区分平台内部异常类型。
 * The single runtime exception exposed by UniWork so callers need not handle platform-specific exception classes.
 */
public class UniWorkException extends RuntimeException {

    /** 使用错误信息创建异常。Creates an exception with a message. */
    public UniWorkException(String message) {
        super(message);
    }

    /** 使用错误信息和原始原因创建异常。Creates an exception with a message and root cause. */
    public UniWorkException(String message, Throwable cause) {
        super(message, cause);
    }
}
