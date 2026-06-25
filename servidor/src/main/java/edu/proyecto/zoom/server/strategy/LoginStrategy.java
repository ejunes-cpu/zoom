package edu.proyecto.zoom.server.strategy;

import com.google.gson.Gson;
import edu.proyecto.zoom.server.factory.MessageFactory;
import edu.proyecto.zoom.server.model.Mensaje;
import edu.proyecto.zoom.server.model.Usuario;
import edu.proyecto.zoom.server.service.ConnectionManager;
import org.java_websocket.WebSocket;

import java.util.List;
import java.util.stream.Collectors;

public class LoginStrategy implements MessageStrategy {
    private final ConnectionManager connectionManager = ConnectionManager.getInstance();
    private final Gson gson = new Gson();

    @Override
    public void processMessage(Mensaje mensaje, WebSocket conn) {
        String username = mensaje.getUsuario();
        
        if (connectionManager.getUsuarioByNombre(username) != null) {
            Mensaje error = MessageFactory.createErrorMessage("El usuario ya existe.", username);
            conn.send(gson.toJson(error));
            return;
        }

        Usuario nuevoUsuario = new Usuario(java.util.UUID.randomUUID().toString(), username, conn);
        connectionManager.addUsuario(conn, nuevoUsuario);

        // Notificar a todos que alguien entró
        Mensaje notificacion = MessageFactory.createSystemMessage(username + " se ha unido al chat.");
        String notificacionJson = gson.toJson(notificacion);

        // Enviar la lista de usuarios actualizada a todos
        List<String> usernames = connectionManager.getAllUsuarios().stream()
                .map(Usuario::getNombre)
                .collect(Collectors.toList());
        Mensaje usersMsg = MessageFactory.createUsersMessage(gson.toJson(usernames), "Todos");
        String usersJson = gson.toJson(usersMsg);

        for (Usuario u : connectionManager.getAllUsuarios()) {
            u.getConexion().send(notificacionJson);
            u.getConexion().send(usersJson);
        }
    }
}
