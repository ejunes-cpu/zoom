package edu.proyecto.zoom.server;

import edu.proyecto.zoom.model.Protocol;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestor de salas de videoconferencia.
 *
 * Responsabilidades:
 *   - Crear y eliminar salas (RoomState)
 *   - Gestionar la cola de espera de usuarios
 *   - Aprobar usuarios de la cola hacia la sala activa
 *   - Hacer broadcast de mensajes y archivos a todos los participantes
 *   - Limpiar recursos cuando un usuario se desconecta
 *
 * Todas las operaciones sobre salas son thread-safe gracias a ConcurrentHashMap
 * y LinkedBlockingQueue.
 */
public class RoomManager {

    private static final Logger LOG = Logger.getLogger(RoomManager.class.getName());

    /**
     * Mapa de salas activas: roomId → RoomState.
     * ConcurrentHashMap permite acceso concurrente sin bloquear toda la estructura.
     */
    private final ConcurrentHashMap<String, RoomState> salas = new ConcurrentHashMap<>();

    // ─── Operaciones de sala ───────────────────────────────────────────────────

    /**
     * Crea una nueva sala y agrega al host como primer participante.
     *
     * @param roomId  identificador único de la sala
     * @param host    nombre del usuario que crea la sala
     * @param handler manejador TCP del host
     * @return true si la sala fue creada, false si ya existía con ese ID
     */
    public boolean createRoom(String roomId, String host, ClientHandler handler) {
        RoomState estado = new RoomState(host);

        // putIfAbsent devuelve null si la clave no existía → sala creada con éxito
        RoomState anterior = salas.putIfAbsent(roomId, estado);
        if (anterior != null) {
            LOG.warning("La sala '" + roomId + "' ya existe.");
            return false;
        }

        // El host entra directamente como participante (no necesita aprobación)
        estado.participantes.put(host, handler);
        LOG.info("Sala creada: " + roomId + " | Host: " + host);
        return true;
    }

    /**
     * Solicita unirse a una sala. El usuario es puesto en cola de espera
     * hasta que el host llame a approveNext().
     *
     * @param roomId   identificador de la sala
     * @param username nombre del usuario que solicita unirse
     * @param handler  manejador TCP del usuario
     * @return posición en la cola (1 = primero), o -1 si la sala no existe
     */
    public int requestJoin(String roomId, String username, ClientHandler handler) {
        RoomState estado = salas.get(roomId);
        if (estado == null) {
            LOG.warning("JOIN_ROOM: sala '" + roomId + "' no encontrada.");
            return -1;
        }

        // Encolar al usuario que quiere unirse
        estado.colaEspera.offer(new WaitingUser(username, handler));

        // Calcular la posición aproximada en la cola
        int posicion = estado.colaEspera.size();
        LOG.info("Usuario '" + username + "' en espera en sala '" + roomId + "' — posición: " + posicion);
        return posicion;
    }

    /**
     * El host aprueba al siguiente usuario de la cola de espera,
     * moviéndolo al mapa de participantes activos y notificándole.
     *
     * @param roomId identificador de la sala
     * @return nombre del usuario aprobado, o null si la cola estaba vacía
     */
    public String approveNext(String roomId) {
        RoomState estado = salas.get(roomId);
        if (estado == null) return null;

        // poll() es no-bloqueante: devuelve null si la cola está vacía
        WaitingUser siguiente = estado.colaEspera.poll();
        if (siguiente == null) {
            LOG.info("Cola de espera vacía en sala '" + roomId + "'");
            return null;
        }

        // Mover al usuario de la cola a los participantes activos
        estado.participantes.put(siguiente.username(), siguiente.handler());

        // Notificar al usuario que fue aprobado
        siguiente.handler().sendText(Protocol.APPROVED + "|" + roomId);
        LOG.info("Usuario '" + siguiente.username() + "' aprobado en sala '" + roomId + "'");
        return siguiente.username();
    }

    // ─── Broadcast ────────────────────────────────────────────────────────────

    /**
     * Envía un mensaje de texto a todos los participantes activos de una sala.
     *
     * @param roomId identificador de la sala
     * @param type   tipo de mensaje (ej. CHAT_RELAY)
     * @param body   cuerpo del mensaje
     */
    public void broadcast(String roomId, String type, String body) {
        RoomState estado = salas.get(roomId);
        if (estado == null) return;

        String mensaje = type + "|" + body;

        // Iterar sobre todos los participantes y enviar el mensaje
        Collection<ClientHandler> participantes = estado.participantes.values();
        for (ClientHandler handler : participantes) {
            handler.sendText(mensaje);
        }
        LOG.fine("Broadcast [" + type + "] en sala '" + roomId + "' a " + participantes.size() + " participantes");
    }

    /**
     * Retransmite los metadatos de un archivo a todos los participantes de la sala.
     * El contenido binario del archivo se transfiere por separado (cliente a cliente
     * o por otro canal en una implementación completa).
     *
     * @param roomId   identificador de la sala
     * @param from     usuario que envía el archivo
     * @param fileName nombre del archivo
     * @param data     datos binarios del archivo
     */
    public void broadcastFile(String roomId, String from, String fileName, byte[] data) {
        RoomState estado = salas.get(roomId);
        if (estado == null) return;

        LOG.info("Broadcast de archivo '" + fileName + "' desde '" + from + "' en sala '" + roomId + "'");

        // Retransmitir metadatos + datos a cada participante excepto al emisor
        for (Map.Entry<String, ClientHandler> entry : estado.participantes.entrySet()) {
            if (!entry.getKey().equals(from)) {
                entry.getValue().sendFile(fileName, data);
            }
        }
    }

    // ─── Desconexión ──────────────────────────────────────────────────────────

    /**
     * Elimina al usuario de todas las salas donde participa o está en espera.
     * Si el usuario era el único participante, la sala se elimina automáticamente.
     *
     * @param username nombre del usuario que se desconecta
     */
    public void disconnect(String username) {
        // Iterar sobre todas las salas y limpiar la presencia del usuario
        salas.entrySet().removeIf(entry -> {
            RoomState estado = entry.getValue();
            String roomId  = entry.getKey();

            // Remover de participantes activos
            estado.participantes.remove(username);

            // Remover de la cola de espera (puede estar en más de una posición)
            estado.colaEspera.removeIf(w -> w.username().equals(username));

            boolean salaVacia = estado.participantes.isEmpty();
            if (salaVacia) {
                LOG.info("Sala '" + roomId + "' eliminada (sin participantes).");
            }
            return salaVacia; // elimina la entrada del mapa si la sala quedó vacía
        });

        LOG.info("Usuario '" + username + "' desconectado y removido de todas las salas.");
    }

    // ─── Clases internas ──────────────────────────────────────────────────────

    /**
     * Estado interno de una sala de videoconferencia.
     *
     * - participantes: usuarios actualmente en la reunión (pueden enviar/recibir)
     * - colaEspera:    usuarios esperando ser aprobados por el host
     * - hostUsername:  nombre del creador/host de la sala
     */
    static final class RoomState {

        /** Participantes activos: username → ClientHandler */
        final ConcurrentHashMap<String, ClientHandler> participantes = new ConcurrentHashMap<>();

        /**
         * Cola FIFO de usuarios esperando aprobación.
         * LinkedBlockingQueue es thread-safe y permite operaciones bloqueantes.
         */
        final BlockingQueue<WaitingUser> colaEspera = new LinkedBlockingQueue<>();

        /** Nombre del usuario que creó la sala */
        final String hostUsername;

        RoomState(String hostUsername) {
            this.hostUsername = hostUsername;
        }
    }

    /**
     * Registro inmutable que representa a un usuario en la cola de espera.
     * Usa Record de Java 16+ para immutabilidad y concisión.
     *
     * @param username nombre del usuario
     * @param handler  referencia al manejador TCP de ese usuario
     */
    record WaitingUser(String username, ClientHandler handler) {}
}
