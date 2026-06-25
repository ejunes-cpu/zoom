package edu.proyecto.zoom.server.command;

import edu.proyecto.zoom.server.model.Mensaje;
import org.java_websocket.WebSocket;

public interface Command {
    void execute(Mensaje mensaje, WebSocket conn);
}
