package org.example;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, World!");

        String host = "0.0.0.0";
        int port = 9999;
        try (XBServer connector = new XBServer(host, port)) {
            for (;;) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}