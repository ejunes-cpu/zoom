package edu.proyecto.zoom.server.strategy;

import com.google.gson.Gson;
import edu.proyecto.zoom.server.decorator.DateMessageDecorator;
import edu.proyecto.zoom.server.decorator.MessageDecorator;
import edu.proyecto.zoom.server.factory.MessageFactory;
import edu.proyecto.zoom.server.model.Mensaje;
import edu.proyecto.zoom.server.model.Usuario;
import edu.proyecto.zoom.server.service.ConnectionManager;
import org.java_websocket.WebSocket;

public class PrivateMessageStrategy implements MessageStrategy {
    private final ConnectionManager connectionManager = ConnectionManager.getInstance();
    private final Gson gson = new Gson();
    private final MessageDecorator decorator = new DateMessageDecorator();

    @Override
    public void processMessage(Mensaje mensaje, WebSocket conn) {
        String destinatario = mensaje.getDestinatario();
        Usuario target = connectionManager.getUsuarioByNombre(destinatario);

        if (target != null) {
            Mensaje mensajeDecorado = decorator.decorate(mensaje);
            String json = gson.toJson(mensajeDecorado);
            target.getConexion().send(json);
            
            // También se lo enviamos al que lo emite para que lo vea en su chat
            conn.send(json);
        } else {
            Mensaje error = MessageFactory.createErrorMessage("El usuario " + destinatario + " no está conectado.", mensaje.getUsuario());
            conn.send(gson.toJson(error));
        }
    }
}
