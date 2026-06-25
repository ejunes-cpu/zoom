package edu.proyecto.zoom.server.strategy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.proyecto.zoom.server.decorator.DateMessageDecorator;
import edu.proyecto.zoom.server.decorator.MessageDecorator;
import edu.proyecto.zoom.server.factory.MessageFactory;
import edu.proyecto.zoom.server.model.Mensaje;
import edu.proyecto.zoom.server.model.Room;
import edu.proyecto.zoom.server.model.TipoMensaje;
import edu.proyecto.zoom.server.model.Usuario;
import edu.proyecto.zoom.server.service.ConnectionManager;
import edu.proyecto.zoom.server.service.WebSocketRoomManager;
import org.java_websocket.WebSocket;

/**
 * Strategy para mensajes de chat dentro de una sala (MSG_SALA).
 *
 * Retransmite el mensaje a todos los participantes de la sala.
 */
public class MsgSalaStrategy implements MessageStrategy {
    private final ConnectionManager connectionManager = ConnectionManager.getInstance();
    private final WebSocketRoomManager roomManager = WebSocketRoomManager.getInstance();
    private final Gson gson = new Gson();
    private final MessageDecorator decorator = new DateMessageDecorator();

    @Override
    public void processMessage(Mensaje mensaje, WebSocket conn) {
        String username = mensaje.getUsuario();
        Usuario usuario = connectionManager.getUsuario(conn);
        if (usuario == null) {
            conn.send(gson.toJson(MessageFactory.createErrorMessage("No estás autenticado.", username)));
            return;
        }

        try {
            JsonObject payload = JsonParser.parseString(mensaje.getContenido()).getAsJsonObject();
            String roomId = payload.get("roomId").getAsString();
            String texto = payload.get("texto").getAsString();

            Room room = roomManager.obtenerSala(roomId);
            if (room == null || !room.contieneParticipante(username)) {
                conn.send(gson.toJson(MessageFactory.createErrorMessage(
                    "No estás en esta sala.", username)));
                return;
            }

            // Construir mensaje para retransmitir
            JsonObject msgPayload = new JsonObject();
            msgPayload.addProperty("roomId", roomId);
            msgPayload.addProperty("usuario", username);
            msgPayload.addProperty("texto", texto);

            Mensaje msgSala = new Mensaje(
                TipoMensaje.MSG_SALA,
                username,
                "Todos",
                msgPayload.toString(),
                ""
            );

            // Decorar con fecha
            Mensaje decorado = decorator.decorate(msgSala);
            String json = gson.toJson(decorado);

            // Enviar a todos los participantes de la sala
            for (String participante : room.getParticipantes()) {
                Usuario u = connectionManager.getUsuarioByNombre(participante);
                if (u != null) {
                    u.getConexion().send(json);
                }
            }

        } catch (Exception e) {
            conn.send(gson.toJson(MessageFactory.createErrorMessage(
                "Error al enviar mensaje de sala: " + e.getMessage(), username)));
        }
    }
}
