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
 * Strategy para salir de una sala (SALIR_SALA).
 *
 * Remueve al usuario de la sala y notifica a los demás participantes.
 * Si la sala queda vacía, se desactiva automáticamente.
 */
public class SalirSalaStrategy implements MessageStrategy {
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

            roomManager.salirDeSala(roomId, username);

            // Confirmar al usuario que salió
            Mensaje confirmacion = new Mensaje(
                TipoMensaje.SISTEMA,
                "Servidor",
                username,
                "Has salido de la sala.",
                ""
            );
            conn.send(gson.toJson(confirmacion));

            // Broadcast participantes actualizados a los que quedan
            Room room = roomManager.obtenerSala(roomId);
            if (room != null && room.isActiva()) {
                broadcastParticipantes(room);
                
                // Enviar mensaje de sistema al chat de la sala
                JsonObject sysMsgPayload = new JsonObject();
                sysMsgPayload.addProperty("roomId", roomId);
                sysMsgPayload.addProperty("usuario", "Sistema");
                sysMsgPayload.addProperty("texto", username + " ha salido de la sala.");

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
            }

            System.out.println("Usuario '" + username + "' salió de sala '" + roomId + "'");

        } catch (Exception e) {
            conn.send(gson.toJson(MessageFactory.createErrorMessage(
                "Error al salir de la sala: " + e.getMessage(), username)));
        }
    }

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

        for (String participante : participantes) {
            Usuario u = connectionManager.getUsuarioByNombre(participante);
            if (u != null) {
                u.getConexion().send(json);
            }
        }
    }
}
