package com.agroshop.demodata;

public class DemoDataSafetyException extends IllegalStateException {

    public DemoDataSafetyException(String message) {
        super(message);
    }

    public DemoDataSafetyException(String message, Throwable cause) {
        super(message, cause);
    }
}
