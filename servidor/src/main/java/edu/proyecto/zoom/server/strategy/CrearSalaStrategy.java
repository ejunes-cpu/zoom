package edu.proyecto.zoom.server.strategy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.proyecto.zoom.server.factory.MessageFactory;
import edu.proyecto.zoom.server.model.Mensaje;
import edu.proyecto.zoom.server.model.Room;
import edu.proyecto.zoom.server.model.RoomInfo;
import edu.proyecto.zoom.server.model.TipoMensaje;
import edu.proyecto.zoom.server.model.Usuario;
import edu.proyecto.zoom.server.service.ConnectionManager;
import edu.proyecto.zoom.server.service.WebSocketRoomManager;
import org.java_websocket.WebSocket;

import java.util.ArrayList;
import java.util.List;

/**
 * Strategy para manejar la creación de salas (CREAR_SALA).
 *
 * Recibe: {tipo: CREAR_SALA, contenido: "{\"nombre\":\"...\",\"tipoSala\":\"LIBRE|PRIVADA\"}"}
 * Envía:  SALA_CREADA al creador con roomId (+ código si PRIVADA)
 */
public class CrearSalaStrategy implements MessageStrategy {
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
            // Parsear el payload JSON del contenido
            JsonObject payload = JsonParser.parseString(mensaje.getContenido()).getAsJsonObject();
            String nombre = payload.get("nombre").getAsString();
            String tipoSala = payload.get("tipoSala").getAsString();
            String codigo = payload.has("codigo") ? payload.get("codigo").getAsString() : null;

            Room.RoomType tipo = Room.RoomType.valueOf(tipoSala.toUpperCase());
            Room room;

            if (tipo == Room.RoomType.PRIVADA) {
                room = roomManager.crearSalaPrivada(nombre, username, codigo);
            } else {
                room = roomManager.crearSalaLibre(nombre, username);
            }

            // Construir respuesta SALA_CREADA
            JsonObject respPayload = new JsonObject();
            respPayload.addProperty("roomId", room.getRoomId());
            respPayload.addProperty("nombre", room.getNombre());
            respPayload.addProperty("tipo", room.getTipo().name());
            if (room.getTipo() == Room.RoomType.PRIVADA) {
                respPayload.addProperty("codigo", room.getCodigoAcceso());
            }

            Mensaje respuesta = new Mensaje(
                TipoMensaje.SALA_CREADA,
                "Servidor",
                username,
                respPayload.toString(),
                ""
            );
            conn.send(gson.toJson(respuesta));

            System.out.println("Sala creada: " + room.getNombre() + " [" + room.getTipo() + "] por " + username);

            // Broadcast de la nueva lista de salas a TODOS los usuarios conectados
            List<RoomInfo> salasPublicas = roomManager.obtenerSalasPublicas();
            String salasJson = gson.toJson(salasPublicas);
            Mensaje broadcastSalas = new Mensaje(
                TipoMensaje.SALAS_DISPONIBLES,
                "Servidor",
                "Todos",
                salasJson,
                ""
            );
            String jsonBroadcast = gson.toJson(broadcastSalas);
            for (Usuario u : connectionManager.getAllUsuarios()) {
                if (u.getConexion().isOpen()) {
                    u.getConexion().send(jsonBroadcast);
                }
            }

        } catch (Exception e) {
            conn.send(gson.toJson(MessageFactory.createErrorMessage(
                "Error al crear sala: " + e.getMessage(), username)));
        }
    }
}
