package edu.proyecto.zoom.server.model;

public enum TipoMensaje {
    // ─── Tipos existentes ─────────────────────────────────────────────────────
    LOGIN,
    LOGOUT,
    MENSAJE,
    PRIVADO,
    USUARIOS,
    ERROR,
    SISTEMA,

    // ─── Salas: cliente → servidor ────────────────────────────────────────────
    CREAR_SALA,         // Crear sala {nombre, tipo}
    LISTA_SALAS,        // Pedir lista de salas
    UNIRSE_SALA,        // Unirse a sala {roomId, codigo?}
    OBTENER_CODIGO,     // Pedir código (solo creador)
    SALIR_SALA,         // Salir de sala {roomId}
    MSG_SALA,           // Mensaje de chat dentro de sala

    // ─── Salas: servidor → cliente ────────────────────────────────────────────
    SALA_CREADA,        // Confirmación + roomId (+ código si PRIVADA)
    SALAS_DISPONIBLES,  // Lista de salas sin códigos
    SALA_UNIDO,         // Confirmación de ingreso
    SALA_ERROR,         // Código incorrecto / sala no existe
    CODIGO_SALA,        // Código (solo al creador)
    PARTICIPANTES_SALA, // Lista actualizada de participantes

    // ─── Compartir Pantalla ───────────────────────────────────────────────────
    SCREEN_START,       // Cliente avisa que empieza a compartir
    SCREEN_STOP,        // Cliente avisa que para de compartir
    SCREEN_FRAME,       // Frame de pantalla (base64 JPEG)
    SCREEN_INFO,        // Servidor notifica quién está compartiendo

    // ─── Audio en Tiempo Real ─────────────────────────────────────────────────
    AUDIO_START,        // Cliente avisa que activa micrófono
    AUDIO_STOP,         // Cliente avisa que desactiva micrófono
    AUDIO_DATA          // Paquete de audio PCM (base64)
}
