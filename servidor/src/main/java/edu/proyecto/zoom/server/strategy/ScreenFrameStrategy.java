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
 * Strategy para retransmitir frames de pantalla compartida (SCREEN_FRAME).
 *
 * Recibe un frame de pantalla (base64 JPEG) del compartidor y lo reenvía
 * a todos los demás participantes de la sala (excepto el emisor).
 */
public class ScreenFrameStrategy implements MessageStrategy {
    private final ConnectionManager connectionManager = ConnectionManager.getInstance();
    private final WebSocketRoomManager roomManager = WebSocketRoomManager.getInstance();
    private final Gson gson = new Gson();

    @Override
    public void processMessage(Mensaje mensaje, WebSocket conn) {
        String username = mensaje.getUsuario();

        try {
            JsonObject payload = JsonParser.parseString(mensaje.getContenido()).getAsJsonObject();
            String roomId = payload.get("roomId").getAsString();
            String frameData = payload.get("frameData").getAsString();

            Room room = roomManager.obtenerSala(roomId);
            if (room == null || !room.contieneParticipante(username)) {
                return; // Silenciosamente ignorar si no es participante
            }

            // Verificar que el emisor es el que está compartiendo
            if (!username.equals(room.getCompartidorPantalla())) {
                return; // Solo el compartidor puede enviar frames
            }

            // Construir el mensaje para retransmitir
            JsonObject framePayload = new JsonObject();
            framePayload.addProperty("roomId", roomId);
            framePayload.addProperty("compartidor", username);
            framePayload.addProperty("frameData", frameData);

            Mensaje frameMsg = new Mensaje(
                TipoMensaje.SCREEN_FRAME,
                username,
                "Todos",
                framePayload.toString(),
                ""
            );
            String json = gson.toJson(frameMsg);

            // Enviar a todos EXCEPTO al emisor
            for (String participante : room.getParticipantes()) {
                if (!participante.equals(username)) {
                    Usuario u = connectionManager.getUsuarioByNombre(participante);
                    if (u != null) {
                        u.getConexion().send(json);
                    }
                }
            }

        } catch (Exception e) {
            // Silenciosamente ignorar errores de frames para no saturar el log
        }
    }
}
