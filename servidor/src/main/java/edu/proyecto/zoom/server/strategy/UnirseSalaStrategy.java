package edu.proyecto.zoom.server.strategy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.proyecto.zoom.server.factory.MessageFactory;
import edu.proyecto.zoom.server.model.Mensaje;
import edu.proyecto.zoom.server.model.Room;
import edu.proyecto.zoom.server.model.TipoMensaje;
import edu.proyecto.zoom.server.model.Usuario;
import edu.proyecto.zoom.server.service.ConnectionManager;
import edu.proyecto.zoom.server.service.WebSocketRoomManager;
import org.java_websocket.WebSocket;

import java.util.ArrayList;
import java.util.List;

/**
 * Strategy para unirse a una sala (UNIRSE_SALA).
 *
 * Soporta tanto salas libres (sin código) como privadas (con código).
 *
 * Recibe: {tipo: UNIRSE_SALA, contenido: "{\"roomId\":\"...\",\"codigo\":\"...\"}"}
 * Envía:  SALA_UNIDO si ok, SALA_ERROR si falla
 * Broadcast: PARTICIPANTES_SALA a todos los participantes de la sala
 */
public class UnirseSalaStrategy implements MessageStrategy {
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

        try {
            JsonObject payload = JsonParser.parseString(mensaje.getContenido()).getAsJsonObject();
            String roomId = payload.get("roomId").getAsString();
            String codigo = payload.has("codigo") ? payload.get("codigo").getAsString() : null;

            Room room = roomManager.obtenerSala(roomId);
            if (room == null) {
                enviarError(conn, username, "La sala no existe.");
                return;
            }

            if (!room.isActiva()) {
                enviarError(conn, username, "La sala ya no está activa.");
                return;
            }

            // Validar código si es sala privada
            if (room.getTipo() == Room.RoomType.PRIVADA) {
                if (codigo == null || !room.validarCodigo(codigo)) {
                    enviarError(conn, username, "Código de acceso incorrecto.");
                    return;
                }
            }

            // Unirse a la sala
            boolean unido = roomManager.unirseASala(roomId, username);
            if (!unido) {
                enviarError(conn, username, "No se pudo unir a la sala.");
                return;
            }

            // Enviar confirmación SALA_UNIDO al usuario
            JsonObject respPayload = new JsonObject();
            respPayload.addProperty("roomId", roomId);
            respPayload.addProperty("nombre", room.getNombre());
            respPayload.addProperty("tipo", room.getTipo().name());
            respPayload.addProperty("esCreador", room.esCreador(username));

            Mensaje respuesta = new Mensaje(
                TipoMensaje.SALA_UNIDO,
                "Servidor",
                username,
                respPayload.toString(),
                ""
            );
            conn.send(gson.toJson(respuesta));

            // Notificar a todos los participantes la lista actualizada
            broadcastParticipantes(room);

            System.out.println("Usuario '" + username + "' se unió a sala '" + room.getNombre() + "'");

            // Enviar mensaje de sistema al chat de la sala
            JsonObject sysMsgPayload = new JsonObject();
            sysMsgPayload.addProperty("roomId", roomId);
            sysMsgPayload.addProperty("usuario", "Sistema");
            sysMsgPayload.addProperty("texto", username + " se ha conectado a la sala.");

            Mensaje sysMsg = new Mensaje(
                TipoMensaje.MSG_SALA,
                "Sistema",
                "Todos",
                sysMsgPayload.toString(),
                ""
            );
            String sysJson = gson.toJson(sysMsg);
            
            for (String participante : room.getParticipantes()) {
                Usuario u = connectionManager.getUsuarioByNombre(participante);
                if (u != null) {
                    u.getConexion().send(sysJson);
                }
            }

        } catch (Exception e) {
            conn.send(gson.toJson(MessageFactory.createErrorMessage(
                "Error al unirse a la sala: " + e.getMessage(), username)));
        }
    }

    private void enviarError(WebSocket conn, String username, String error) {
        Mensaje errorMsg = new Mensaje(
            TipoMensaje.SALA_ERROR,
            "Servidor",
            username,
            error,
            ""
        );
        conn.send(gson.toJson(errorMsg));
    }

    /**
     * Envía la lista de participantes actualizada a todos los miembros de la sala.
     */
    private void broadcastParticipantes(Room room) {
        List<String> participantes = new ArrayList<>(room.getParticipantes());

        JsonObject payload = new JsonObject();
        payload.addProperty("roomId", room.getRoomId());
        payload.add("participantes", gson.toJsonTree(participantes));

        Mensaje msg = new Mensaje(
            TipoMensaje.PARTICIPANTES_SALA,
            "Servidor",
            "Todos",
            payload.toString(),
            ""
        );
        String json = gson.toJson(msg);

        // Enviar a todos los participantes de la sala
        for (String participante : participantes) {
            Usuario u = connectionManager.getUsuarioByNombre(participante);
            if (u != null) {
                u.getConexion().send(json);
            }
        }
    }
}
