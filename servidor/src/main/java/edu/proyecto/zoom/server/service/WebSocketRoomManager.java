package edu.proyecto.zoom.server.service;

import edu.proyecto.zoom.server.model.Room;
import edu.proyecto.zoom.server.model.RoomInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Gestor de salas para el sistema WebSocket.
 *
 * Responsabilidades:
 *   - Crear salas libres y privadas
 *   - Gestionar participantes (unirse, salir)
 *   - Obtener listas públicas de salas (sin códigos)
 *   - Validar permisos de acceso a códigos
 *   - Limpiar salas vacías
 *
 * Singleton — todas las operaciones son thread-safe vía ConcurrentHashMap.
 */
public class WebSocketRoomManager {

    private static final Logger LOG = Logger.getLogger(WebSocketRoomManager.class.getName());

    private static WebSocketRoomManager instance;

    /** Mapa de salas activas: roomId → Room */
    private final ConcurrentHashMap<String, Room> salas = new ConcurrentHashMap<>();

    private WebSocketRoomManager() {}

    public static synchronized WebSocketRoomManager getInstance() {
        if (instance == null) {
            instance = new WebSocketRoomManager();
            // Crear un par de salas de prueba para que la tabla no esté vacía inicialmente
            instance.crearSalaLibre("Sala General (Prueba)", "Sistema");
            instance.crearSalaPrivada("Reunión Privada", "Sistema", null);
        }
        return instance;
    }

    // ─── Crear salas ──────────────────────────────────────────────────────────

    /**
     * Crea una sala de acceso libre.
     *
     * @param nombre          nombre visible de la sala
     * @param creadorUsername  username del creador
     * @return la sala recién creada
     */
    public Room crearSalaLibre(String nombre, String creadorUsername) {
        String roomId = UUID.randomUUID().toString().substring(0, 8);
        Room room = new Room(roomId, nombre, Room.RoomType.LIBRE, creadorUsername, null);
        salas.put(roomId, room);
        LOG.info("Sala LIBRE creada: '" + nombre + "' [" + roomId + "] por " + creadorUsername);
        return room;
    }

    /**
     * Crea una sala privada con código de acceso personalizado o generado automáticamente.
     *
     * @param nombre          nombre visible de la sala
     * @param creadorUsername  username del creador
     * @param customCodigo     código de acceso (si es null se genera uno)
     * @return la sala recién creada (con código)
     */
    public Room crearSalaPrivada(String nombre, String creadorUsername, String customCodigo) {
        String roomId = UUID.randomUUID().toString().substring(0, 8);
        Room room = new Room(roomId, nombre, Room.RoomType.PRIVADA, creadorUsername, customCodigo);
        salas.put(roomId, room);
        LOG.info("Sala PRIVADA creada: '" + nombre + "' [" + roomId + "] por " + creadorUsername
                 + " | Código: " + room.getCodigoAcceso());
        return room;
    }

    // ─── Unirse a salas ───────────────────────────────────────────────────────

    /**
     * Unirse a una sala (libre o privada, la validación del código
     * debe hacerse ANTES de llamar este método).
     */
    public boolean unirseASala(String roomId, String username) {
        Room room = salas.get(roomId);
        if (room == null || !room.isActiva()) return false;
        return room.agregarParticipante(username);
    }

    // ─── Consultas ────────────────────────────────────────────────────────────

    /**
     * Obtiene la lista de salas activas como DTOs públicos (sin códigos).
     */
    public List<RoomInfo> obtenerSalasPublicas() {
        List<RoomInfo> lista = new ArrayList<>();
        for (Room room : salas.values()) {
            if (room.isActiva()) {
                lista.add(RoomInfo.fromRoom(room));
            }
        }
        return lista;
    }

    /**
     * Obtiene una sala por su ID.
     */
    public Room obtenerSala(String roomId) {
        return salas.get(roomId);
    }

    /**
     * Obtiene el código de acceso de una sala SOLO si el solicitante es el creador.
     *
     * @return el código, o null si no tiene permisos
     */
    public String obtenerCodigo(String roomId, String solicitanteUsername) {
        Room room = salas.get(roomId);
        if (room == null) return null;
        if (!room.esCreador(solicitanteUsername)) return null;
        return room.getCodigoAcceso();
    }

    // ─── Salir de sala ────────────────────────────────────────────────────────

    /**
     * Remueve al usuario de la sala.
     * Si la sala queda vacía, se desactiva y remueve.
     */
    public void salirDeSala(String roomId, String username) {
        Room room = salas.get(roomId);
        if (room == null) return;

        room.removerParticipante(username);

        if (room.getCantidadParticipantes() == 0) {
            room.desactivar();
            salas.remove(roomId);
            LOG.info("Sala '" + roomId + "' eliminada (sin participantes).");
        }
    }

    /**
     * Verifica si un usuario es el creador de una sala.
     */
    public boolean esCreador(String roomId, String username) {
        Room room = salas.get(roomId);
        if (room == null) return false;
        return room.esCreador(username);
    }

    /**
     * Desconecta a un usuario de todas las salas donde participa.
     * Llamar cuando el usuario cierra la conexión WebSocket.
     */
    public void desconectarUsuario(String username) {
        salas.entrySet().removeIf(entry -> {
            Room room = entry.getValue();
            room.removerParticipante(username);
            boolean vacia = room.getCantidadParticipantes() == 0;
            if (vacia) {
                room.desactivar();
                LOG.info("Sala '" + entry.getKey() + "' eliminada (sin participantes).");
            }
            return vacia;
        });
    }
}
