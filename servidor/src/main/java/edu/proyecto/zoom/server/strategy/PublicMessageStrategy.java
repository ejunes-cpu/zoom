package edu.proyecto.zoom.server.strategy;

import com.google.gson.Gson;
import edu.proyecto.zoom.server.decorator.DateMessageDecorator;
import edu.proyecto.zoom.server.decorator.MessageDecorator;
import edu.proyecto.zoom.server.model.Mensaje;
import edu.proyecto.zoom.server.model.Usuario;
import edu.proyecto.zoom.server.service.ConnectionManager;
import org.java_websocket.WebSocket;

public class PublicMessageStrategy implements MessageStrategy {
    private final ConnectionManager connectionManager = ConnectionManager.getInstance();
    private final Gson gson = new Gson();
    private final MessageDecorator decorator = new DateMessageDecorator();

    @Override
    public void processMessage(Mensaje mensaje, WebSocket conn) {
        Mensaje mensajeDecorado = decorator.decorate(mensaje);
        String json = gson.toJson(mensajeDecorado);

        for (Usuario u : connectionManager.getAllUsuarios()) {
            u.getConexion().send(json);
        }
    }
}
