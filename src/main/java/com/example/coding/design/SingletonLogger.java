package com.example.coding.design;

public class SingletonLogger {

    private static volatile SingletonLogger instance =null;
    private SingletonLogger(){};

    public static SingletonLogger getInstance() {
        if(instance==null) {
            synchronized (SingletonLogger.class) {
                if(instance==null) {
                    instance=new SingletonLogger();
                }
            }
        }
        return instance;
    }

    public void message(String message) {
        System.out.println("[LOG]=====>" + message);
    }

    public void message(int message) {
        System.out.println("[LOG]=====>" + message);
    }

}
