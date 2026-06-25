package edu.proyecto.zoom.server.strategy;

import edu.proyecto.zoom.server.model.Mensaje;
import org.java_websocket.WebSocket;

public interface MessageStrategy {
    void processMessage(Mensaje mensaje, WebSocket conn);
}
