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

/**
 * Strategy para iniciar/detener screen sharing (SCREEN_START, SCREEN_STOP).
 *
 * Cuando un usuario empieza a compartir, se registra en la sala.
 * Se notifica a todos los demás participantes quién está compartiendo.
 */
public class ScreenShareStrategy implements MessageStrategy {
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

            Room room = roomManager.obtenerSala(roomId);
            if (room == null || !room.contieneParticipante(username)) {
                conn.send(gson.toJson(MessageFactory.createErrorMessage(
                    "No estás en esta sala.", username)));
                return;
            }

            TipoMensaje tipo = mensaje.getTipo();

            if (tipo == TipoMensaje.SCREEN_START) {
                // Verificar que nadie más esté compartiendo
                if (room.alguienCompartiendo()) {
                    conn.send(gson.toJson(MessageFactory.createErrorMessage(
                        "Ya hay alguien compartiendo pantalla: " + room.getCompartidorPantalla(), username)));
                    return;
                }
                room.setCompartidorPantalla(username);
                System.out.println("Screen share iniciado por '" + username + "' en sala '" + roomId + "'");
            } else if (tipo == TipoMensaje.SCREEN_STOP) {
                room.setCompartidorPantalla(null);
                System.out.println("Screen share detenido por '" + username + "' en sala '" + roomId + "'");
            }

            // Notificar a todos los participantes quién está compartiendo (o quién dejó de compartir)
            JsonObject infoPayload = new JsonObject();
            infoPayload.addProperty("roomId", roomId);
            infoPayload.addProperty("compartidor", username); // Usar username local, no el de la room que acaba de ponerse en null
            infoPayload.addProperty("activo", tipo == TipoMensaje.SCREEN_START);

            Mensaje infoMsg = new Mensaje(
                TipoMensaje.SCREEN_INFO,
                "Servidor",
                "Todos",
                infoPayload.toString(),
                ""
            );
            String json = gson.toJson(infoMsg);

            for (String participante : room.getParticipantes()) {
                Usuario u = connectionManager.getUsuarioByNombre(participante);
                if (u != null) {
                    u.getConexion().send(json);
                }
            }

        } catch (Exception e) {
            conn.send(gson.toJson(MessageFactory.createErrorMessage(
                "Error en screen share: " + e.getMessage(), username)));
        }
    }
}
