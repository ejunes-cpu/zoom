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
 * Strategy para obtener el código de una sala privada (OBTENER_CODIGO).
 *
 * Solo el creador de la sala puede obtener el código.
 * Cualquier otro usuario recibe SALA_ERROR.
 */
public class ObtenerCodigoStrategy implements MessageStrategy {
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
            if (room != null && room.esCreador(username)) {
                if (room.getTipo() == Room.RoomType.LIBRE) {
                    Mensaje error = new Mensaje(
                        TipoMensaje.SALA_ERROR,
                        "Servidor",
                        username,
                        "Esta sala es pública y no requiere código de acceso.",
                        ""
                    );
                    conn.send(gson.toJson(error));
                    return;
                }
            }

            String codigo = roomManager.obtenerCodigo(roomId, username);

            if (codigo != null) {
                JsonObject respPayload = new JsonObject();
                respPayload.addProperty("roomId", roomId);
                respPayload.addProperty("codigo", codigo);

                Mensaje respuesta = new Mensaje(
                    TipoMensaje.CODIGO_SALA,
                    "Servidor",
                    username,
                    respPayload.toString(),
                    ""
                );
                conn.send(gson.toJson(respuesta));
            } else {
                Mensaje error = new Mensaje(
                    TipoMensaje.SALA_ERROR,
                    "Servidor",
                    username,
                    "No tienes permiso para ver el código o la sala no existe.",
                    ""
                );
                conn.send(gson.toJson(error));
            }

        } catch (Exception e) {
            conn.send(gson.toJson(MessageFactory.createErrorMessage(
                "Error al obtener código: " + e.getMessage(), username)));
        }
    }
}
