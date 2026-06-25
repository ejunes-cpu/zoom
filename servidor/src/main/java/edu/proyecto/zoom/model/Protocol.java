package edu.proyecto.zoom.model;

/**
 * Protocolo de comunicación TCP/UDP para la aplicación tipo Zoom.
 * Todas las constantes son compartidas entre cliente y servidor.
 *
 * Flujo típico:
 *   Cliente → LOGIN          → Servidor responde LOGIN_OK / LOGIN_FAIL
 *   Cliente → CREATE_ROOM    → Servidor crea sala y notifica
 *   Cliente → JOIN_ROOM      → Servidor responde WAITING / APPROVED / REJECTED
 *   Cliente → CHAT           → Servidor hace broadcast con CHAT_RELAY
 *   Cliente → FILE_META      → Servidor retransmite con FILE_RELAY
 *   Cliente → APPROVE_NEXT   → El host aprueba al siguiente en cola
 *   Cliente → LOGOUT         → Servidor desconecta y limpia recursos
 */
public final class Protocol {

    // ─── Puertos de comunicación ───────────────────────────────────────────────
    /** Puerto TCP principal para comandos y chat */
    public static final int TCP_PORT = 5000;

    /** Puerto UDP para heartbeat de presencia */
    public static final int UDP_PORT = 5001;

    // ─── Comandos del cliente al servidor ─────────────────────────────────────
    /** Inicio de sesión: LOGIN|username|password */
    public static final String LOGIN        = "LOGIN";

    /** Crear sala:  CREATE_ROOM|roomId */
    public static final String CREATE_ROOM  = "CREATE_ROOM";

    /** Unirse a sala: JOIN_ROOM|roomId */
    public static final String JOIN_ROOM    = "JOIN_ROOM";

    /** Mensaje de chat: CHAT|roomId|mensaje */
    public static final String CHAT         = "CHAT";

    /** Metadatos de archivo: FILE_META|roomId|fileName|fileSize */
    public static final String FILE_META    = "FILE_META";

    /** Cerrar sesión: LOGOUT */
    public static final String LOGOUT       = "LOGOUT";

    /** El host aprueba al siguiente en cola: APPROVE_NEXT|roomId */
    public static final String APPROVE_NEXT = "APPROVE_NEXT";

    // Nuevos comandos para Salas
    public static final String SALA_CREADA        = "SALA_CREADA";
    public static final String LISTA_SALAS        = "LISTA_SALAS";
    public static final String SALAS_DISPONIBLES  = "SALAS_DISPONIBLES";
    public static final String UNIRSE_SALA        = "UNIRSE_SALA";
    public static final String SALA_UNIDO         = "SALA_UNIDO";
    public static final String SALA_ERROR         = "SALA_ERROR";
    public static final String OBTENER_CODIGO     = "OBTENER_CODIGO";
    public static final String CODIGO_SALA        = "CODIGO_SALA";
    public static final String SALIR_SALA         = "SALIR_SALA";
    public static final String MSG_SALA           = "MSG_SALA";
    public static final String PARTICIPANTES_SALA = "PARTICIPANTES_SALA";

    // ─── Respuestas del servidor al cliente ────────────────────────────────────
    /** Autenticación exitosa */
    public static final String LOGIN_OK     = "LOGIN_OK";

    /** Credenciales inválidas */
    public static final String LOGIN_FAIL   = "LOGIN_FAIL";

    /** El usuario fue puesto en cola de espera: WAITING|posición */
    public static final String WAITING      = "WAITING";

    /** El usuario fue aprobado y ahora es participante */
    public static final String APPROVED     = "APPROVED";

    /** El usuario fue rechazado (sala llena o no existe) */
    public static final String REJECTED     = "REJECTED";

    /** Retransmisión de chat a participantes: CHAT_RELAY|roomId|from|mensaje */
    public static final String CHAT_RELAY   = "CHAT_RELAY";

    /** Retransmisión de metadatos de archivo: FILE_RELAY|roomId|from|fileName|fileSize */
    public static final String FILE_RELAY   = "FILE_RELAY";

    // Constructor privado: clase de utilidad, no instanciable
    private Protocol() {}
}
