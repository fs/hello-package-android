package com.example.tthellopackage;

public class Logger {

    private static Logger mInstance = new Logger();

    public static Logger getInstance() {
        return mInstance;
    }

    private Logger() {
    }

    public void Log(String msg) {
        System.out.println("VidyoConnector App: " + msg);
    }
}
