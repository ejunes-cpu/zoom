package edu.proyecto.zoom.server;

import edu.proyecto.zoom.server.config.ServerConfig;
import edu.proyecto.zoom.server.websocket.ChatWebSocketServer;

import java.net.InetSocketAddress;

public class ZoomServer {
    public static void main(String[] args) {
        ChatWebSocketServer server = new ChatWebSocketServer(new InetSocketAddress(ServerConfig.HOST, ServerConfig.PORT));
        server.start();
        
        System.out.println("Servidor iniciado exitosamente en " + ServerConfig.HOST + ":" + ServerConfig.PORT);
    }
}
