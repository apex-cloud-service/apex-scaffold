package com.apex.scaffold.common.license;

/**
 * License异常
 *
 * @author Apex Team
 * @version 1.0.0
 */
public class LicenseException extends RuntimeException {

    public LicenseException(String message) {
        super(message);
    }

    public LicenseException(String message, Throwable cause) {
        super(message, cause);
    }
}
