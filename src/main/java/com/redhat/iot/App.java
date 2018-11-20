package com.redhat.iot;
import org.h2.tools.Server;



public class App {
    public static void main(String[] args) throws Exception {

        //Server.createTcpServer(args);
        //Server.createWebServer();

        SimpleKapuaInstance.start();
        while(true){}
    }
}
