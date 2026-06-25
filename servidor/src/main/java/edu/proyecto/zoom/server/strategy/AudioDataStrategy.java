package edu.proyecto.zoom.server.strategy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.proyecto.zoom.server.model.Mensaje;
import edu.proyecto.zoom.server.model.Room;
import edu.proyecto.zoom.server.model.TipoMensaje;
import edu.proyecto.zoom.server.model.Usuario;
import edu.proyecto.zoom.server.service.ConnectionManager;
import edu.proyecto.zoom.server.service.WebSocketRoomManager;
import org.java_websocket.WebSocket;

/**
 * Strategy para retransmitir paquetes de audio en tiempo real (AUDIO_DATA).
 *
 * A diferencia de screen sharing (1 compartidor), el audio permite
 * MÚLTIPLES emisores simultáneos en la misma sala.
 *
 * Recibe un paquete de audio PCM codificado en Base64 del emisor y lo reenvía
 * a todos los demás participantes de la sala (excluyendo al emisor para
 * evitar eco).
 */
public class AudioDataStrategy implements MessageStrategy {

    private final ConnectionManager connectionManager = ConnectionManager.getInstance();
    private final WebSocketRoomManager roomManager = WebSocketRoomManager.getInstance();
    private final Gson gson = new Gson();

    @Override
    public void processMessage(Mensaje mensaje, WebSocket conn) {
        String username = mensaje.getUsuario();

        try {
            JsonObject payload = JsonParser.parseString(mensaje.getContenido()).getAsJsonObject();
            String roomId = payload.get("roomId").getAsString();

            Room room = roomManager.obtenerSala(roomId);
            if (room == null || !room.contieneParticipante(username)) {
                return; // Ignorar silenciosamente
            }

            if (mensaje.getTipo() == TipoMensaje.AUDIO_DATA) {
                String audioData = payload.get("audioData").getAsString();
                int rms = payload.has("rms") ? payload.get("rms").getAsInt() : 0;
                long timestamp = payload.has("timestamp") ? payload.get("timestamp").getAsLong() : 0;
                
                System.out.println("[SERVIDOR] AUDIO_DATA recibido de " + username + " en sala " + roomId + " | RMS: " + rms);

                // Construir mensaje para retransmitir
                JsonObject audioPayload = new JsonObject();
                audioPayload.addProperty("roomId", roomId);
                audioPayload.addProperty("emisor", username);
                audioPayload.addProperty("audioData", audioData);
                audioPayload.addProperty("rms", rms);
                audioPayload.addProperty("timestamp", timestamp);

                Mensaje audioMsg = new Mensaje(
                    TipoMensaje.AUDIO_DATA,
                    username,
                    "Todos",
                    audioPayload.toString(),
                    ""
                );
                String json = gson.toJson(audioMsg);

                // Broadcast a todos EXCEPTO al emisor (evitar eco)
                int enviados = 0;
                for (String participante : room.getParticipantes()) {
                    if (!participante.equals(username)) {
                        Usuario u = connectionManager.getUsuarioByNombre(participante);
                        if (u != null) {
                            u.getConexion().send(json);
                            enviados++;
                        }
                    }
                }
                System.out.println("[SERVIDOR] AUDIO_DATA reenviado a " + enviados + " participantes.");
            } else if (mensaje.getTipo() == TipoMensaje.AUDIO_START) {
                System.out.println("[SERVIDOR] " + username + " activó su micrófono en sala " + roomId);
            } else if (mensaje.getTipo() == TipoMensaje.AUDIO_STOP) {
                System.out.println("[SERVIDOR] " + username + " desactivó su micrófono en sala " + roomId);
            }

        } catch (Exception e) {
            // Ignorar silenciosamente para no saturar logs con paquetes de audio
            e.printStackTrace(); // Agregado temporalmente para ver si hay otros errores
        }
    }
}
