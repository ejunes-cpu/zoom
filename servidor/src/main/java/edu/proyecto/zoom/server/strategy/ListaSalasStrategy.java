package edu.proyecto.zoom.server.strategy;

import com.google.gson.Gson;
import edu.proyecto.zoom.server.factory.MessageFactory;
import edu.proyecto.zoom.server.model.Mensaje;
import edu.proyecto.zoom.server.model.RoomInfo;
import edu.proyecto.zoom.server.model.TipoMensaje;
import edu.proyecto.zoom.server.model.Usuario;
import edu.proyecto.zoom.server.service.ConnectionManager;
import edu.proyecto.zoom.server.service.WebSocketRoomManager;
import org.java_websocket.WebSocket;

import java.util.List;

/**
 * Strategy para listar las salas disponibles (LISTA_SALAS).
 *
 * Recibe: {tipo: LISTA_SALAS}
 * Envía:  SALAS_DISPONIBLES con la lista de salas (sin códigos de acceso)
 */
public class ListaSalasStrategy implements MessageStrategy {
    private final ConnectionManager connectionManager = ConnectionManager.getInstance();
    private final WebSocketRoomManager roomManager = WebSocketRoomManager.getInstance();
    private final Gson gson = new Gson();

    @Override
    public void processMessage(Mensaje mensaje, WebSocket conn) {
        String username = mensaje.getUsuario();
        Usuario usuario = connectionManager.getUsuario(conn);
        if (usuario == null) {
            conn.send(gson.toJson(MessageFactory.createErrorMessage("No estás autenticado.", username)));
            return;
        }

        List<RoomInfo> salas = roomManager.obtenerSalasPublicas();
        String salasJson = gson.toJson(salas);

        Mensaje respuesta = new Mensaje(
            TipoMensaje.SALAS_DISPONIBLES,
            "Servidor",
            username,
            salasJson,
            ""
        );
        conn.send(gson.toJson(respuesta));
    }
}
