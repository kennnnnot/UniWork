package com.idongxia.uniwork;

/**
 * The single public exception type raised by UniWork operations.
 */
public class UniWorkException extends RuntimeException {

    public UniWorkException(String message) {
        super(message);
    }

    public UniWorkException(String message, Throwable cause) {
        super(message, cause);
    }
}
