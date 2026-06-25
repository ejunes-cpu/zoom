package edu.proyecto.zoom.server;

import edu.proyecto.zoom.model.Protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manejador de un cliente TCP individual.
 *
 * Cada instancia de ClientHandler corre en su propio hilo del pool
 * (proporcionado por ZoomServer). Lee comandos del cliente en un bucle,
 * los valida y delega la lógica de negocio al RoomManager.
 *
 * Protocolo de mensajes:
 *   Todos los mensajes son Strings UTF-8 delimitados por DataInputStream/DataOutputStream.
 *   Formato: COMANDO|param1|param2|...
 *
 * Para transferencia de archivos se usa un protocolo binario:
 *   1. sendText("FILE_META|roomId|fileName|fileSize")
 *   2. sendFile(fileName, bytes)  →  writeUTF(fileName) + writeInt(len) + write(bytes)
 */
public class ClientHandler implements Runnable {

    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());

    // ─── Referencias compartidas ───────────────────────────────────────────────
    private final Socket socket;
    private final RoomManager roomManager;

    // ─── Streams de comunicación ───────────────────────────────────────────────
    private DataInputStream  entrada;
    private DataOutputStream salida;

    // ─── Estado del cliente ────────────────────────────────────────────────────
    /** Nombre de usuario autenticado; null si aún no hizo LOGIN */
    private String username = null;

    /** true si el cliente completó el LOGIN correctamente */
    private boolean autenticado = false;

    /**
     * @param socket      socket TCP aceptado por ZoomServer
     * @param roomManager gestor compartido de salas
     */
    public ClientHandler(Socket socket, RoomManager roomManager) {
        this.socket      = socket;
        this.roomManager = roomManager;
    }

    // ─── Ciclo principal ───────────────────────────────────────────────────────

    /**
     * Bucle principal del hilo:
     *   1. Inicializa los streams.
     *   2. Lee comandos en loop hasta EOFException o error.
     *   3. En el bloque finally cierra el socket y desconecta al usuario.
     */
    @Override
    public void run() {
        try {
            // Inicializar streams de entrada/salida sobre el socket TCP
            entrada = new DataInputStream(socket.getInputStream());
            salida  = new DataOutputStream(socket.getOutputStream());

            LOG.info("Cliente conectado: " + socket.getInetAddress().getHostAddress());

            // Bucle de lectura de comandos
            String linea;
            while ((linea = entrada.readUTF()) != null) {
                procesarComando(linea);
            }

        } catch (EOFException e) {
            // El cliente cerró la conexión limpiamente (fin de stream)
            LOG.info("Cliente '" + username + "' cerró la conexión (EOF).");

        } catch (IOException e) {
            // Error de red inesperado
            LOG.log(Level.WARNING, "IOException con cliente '" + username + "': " + e.getMessage());

        } finally {
            // Limpiar recursos siempre, independientemente de cómo terminó el bucle
            cerrarConexion();
        }
    }

    // ─── Procesamiento de comandos ─────────────────────────────────────────────

    /**
     * Parsea y despacha el comando recibido del cliente.
     * Formato esperado: COMANDO|param1|param2|...
     *
     * @param linea línea completa recibida por readUTF()
     */
    private void procesarComando(String linea) {
        // Separar el comando de sus parámetros por el delimitador '|'
        String[] partes  = linea.split("\\|", -1);
        String   comando = partes[0].trim().toUpperCase();

        switch (comando) {

            // ── LOGIN ─────────────────────────────────────────────────────────
            case Protocol.LOGIN -> {
                // Formato: LOGIN|username|password
                if (partes.length < 3) {
                    sendText(Protocol.LOGIN_FAIL + "|Formato inválido");
                    return;
                }
                String user = partes[1];
                String pass = partes[2];

                if (AuthService.validate(user, pass)) {
                    // Guardar estado de autenticación
                    this.username    = user;
                    this.autenticado = true;
                    sendText(Protocol.LOGIN_OK + "|Bienvenido " + user);
                    LOG.info("LOGIN exitoso: " + user);
                } else {
                    sendText(Protocol.LOGIN_FAIL + "|Credenciales incorrectas");
                    LOG.warning("LOGIN fallido para usuario: " + partes[1]);
                }
            }

            // ── CREATE_ROOM ───────────────────────────────────────────────────
            case Protocol.CREATE_ROOM -> {
                // Verificar autenticación antes de operar
                if (!verificarAutenticacion()) return;

                // Formato: CREATE_ROOM|roomId
                if (partes.length < 2) {
                    sendText(Protocol.REJECTED + "|Falta el ID de sala");
                    return;
                }
                String roomId = partes[1];

                boolean creada = roomManager.createRoom(roomId, username, this);
                if (creada) {
                    sendText("ROOM_CREATED|" + roomId);
                } else {
                    sendText(Protocol.REJECTED + "|La sala '" + roomId + "' ya existe");
                }
            }

            // ── JOIN_ROOM ─────────────────────────────────────────────────────
            case Protocol.JOIN_ROOM -> {
                if (!verificarAutenticacion()) return;

                // Formato: JOIN_ROOM|roomId
                if (partes.length < 2) {
                    sendText(Protocol.REJECTED + "|Falta el ID de sala");
                    return;
                }
                String roomId = partes[1];

                int posicion = roomManager.requestJoin(roomId, username, this);
                if (posicion == -1) {
                    sendText(Protocol.REJECTED + "|Sala '" + roomId + "' no encontrada");
                } else {
                    sendText(Protocol.WAITING + "|" + posicion);
                }
            }

            // ── APPROVE_NEXT ──────────────────────────────────────────────────
            case Protocol.APPROVE_NEXT -> {
                if (!verificarAutenticacion()) return;

                // Formato: APPROVE_NEXT|roomId
                if (partes.length < 2) {
                    sendText(Protocol.REJECTED + "|Falta el ID de sala");
                    return;
                }
                String roomId = partes[1];

                String aprobado = roomManager.approveNext(roomId);
                if (aprobado != null) {
                    sendText("APPROVED_USER|" + aprobado);
                } else {
                    sendText("QUEUE_EMPTY|" + roomId);
                }
            }

            // ── CHAT ──────────────────────────────────────────────────────────
            case Protocol.CHAT -> {
                if (!verificarAutenticacion()) return;

                // Formato: CHAT|roomId|mensaje
                if (partes.length < 3) {
                    sendText(Protocol.REJECTED + "|Formato CHAT inválido");
                    return;
                }
                String roomId  = partes[1];
                String mensaje = partes[2];

                // Retransmitir el chat a todos los participantes de la sala
                roomManager.broadcast(roomId, Protocol.CHAT_RELAY,
                    roomId + "|" + username + "|" + mensaje);
            }

            // ── FILE_META ─────────────────────────────────────────────────────
            case Protocol.FILE_META -> {
                if (!verificarAutenticacion()) return;

                // Formato: FILE_META|roomId|fileName|fileSize
                if (partes.length < 4) {
                    sendText(Protocol.REJECTED + "|Formato FILE_META inválido");
                    return;
                }
                String roomId   = partes[1];
                String fileName = partes[2];
                String fileSize = partes[3];

                // Leer el contenido binario del archivo tras los metadatos
                try {
                    int    longitud = Integer.parseInt(fileSize);
                    byte[] datos    = new byte[longitud];
                    entrada.readFully(datos); // bloqueante hasta leer todos los bytes

                    // Retransmitir archivo a los demás participantes
                    roomManager.broadcastFile(roomId, username, fileName, datos);

                    // Notificar a los demás participantes con los metadatos
                    roomManager.broadcast(roomId, Protocol.FILE_RELAY,
                        roomId + "|" + username + "|" + fileName + "|" + fileSize);

                } catch (NumberFormatException e) {
                    sendText(Protocol.REJECTED + "|fileSize inválido: " + fileSize);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Error leyendo archivo de '" + username + "'", e);
                }
            }

            // ── LOGOUT ────────────────────────────────────────────────────────
            case Protocol.LOGOUT -> {
                LOG.info("LOGOUT solicitado por: " + username);
                sendText("BYE|Sesión cerrada");
                // cerrarConexion() se llamará en el finally del run()
                // Forzar cierre del socket para salir del bucle
                try { socket.close(); } catch (IOException ignored) {}
            }

            // ── Comando desconocido ────────────────────────────────────────────
            default -> {
                LOG.warning("Comando desconocido de '" + username + "': " + comando);
                sendText("ERROR|Comando no reconocido: " + comando);
            }
        }
    }

    // ─── Métodos de envío (sincronizados) ─────────────────────────────────────

    /**
     * Envía un mensaje de texto UTF-8 al cliente de forma sincronizada.
     * La sincronización evita que dos hilos escriban en el stream al mismo tiempo.
     *
     * @param mensaje texto a enviar (formato: COMANDO|param1|param2)
     */
    public synchronized void sendText(String mensaje) {
        try {
            salida.writeUTF(mensaje);
            salida.flush();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error enviando texto a '" + username + "': " + e.getMessage());
        }
    }

    /**
     * Envía un archivo binario al cliente de forma sincronizada.
     * Protocolo: writeUTF(fileName) + writeInt(length) + write(bytes)
     *
     * @param fileName nombre del archivo
     * @param datos    contenido binario del archivo
     */
    public synchronized void sendFile(String fileName, byte[] datos) {
        try {
            salida.writeUTF(fileName);        // nombre del archivo
            salida.writeInt(datos.length);    // longitud en bytes (4 bytes)
            salida.write(datos);              // contenido binario
            salida.flush();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error enviando archivo '" + fileName + "' a '" + username + "': " + e.getMessage());
        }
    }

    // ─── Utilidades privadas ───────────────────────────────────────────────────

    /**
     * Verifica si el cliente está autenticado.
     * Si no lo está, envía LOGIN_FAIL y devuelve false.
     *
     * @return true si el cliente está autenticado
     */
    private boolean verificarAutenticacion() {
        if (!autenticado) {
            sendText(Protocol.LOGIN_FAIL + "|Debes hacer LOGIN primero");
            return false;
        }
        return true;
    }

    /**
     * Cierra el socket TCP y notifica al RoomManager para limpiar
     * la presencia del usuario en todas las salas.
     */
    private void cerrarConexion() {
        // Notificar al RoomManager que el usuario se desconectó
        if (username != null) {
            roomManager.disconnect(username);
        }

        // Cerrar el socket si aún está abierto
        if (!socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Error cerrando socket de '" + username + "'", e);
            }
        }
        LOG.info("Conexión cerrada para usuario: " + username);
    }

    /** @return nombre de usuario autenticado, o null si no hizo LOGIN */
    public String getUsername() {
        return username;
    }
}
