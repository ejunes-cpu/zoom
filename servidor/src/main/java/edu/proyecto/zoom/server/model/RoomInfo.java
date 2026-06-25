package edu.proyecto.zoom.server.model;

/**
 * DTO (Data Transfer Object) de información pública de una sala.
 *
 * Se usa para enviar la lista de salas a los clientes.
 * NUNCA incluye el código de acceso, solo información pública:
 *   - roomId, nombre, tipo, cantidad de participantes
 *
 * Es un Record de Java 16+ para inmutabilidad y concisión.
 */
public record RoomInfo(
    String roomId,
    String nombre,
    String tipo,              // "LIBRE" o "PRIVADA"
    int cantidadParticipantes,
    String creador
) {
    /**
     * Factory method para crear un RoomInfo desde un Room.
     * Garantiza que el código de acceso NUNCA se filtre.
     */
    public static RoomInfo fromRoom(Room room) {
        return new RoomInfo(
            room.getRoomId(),
            room.getNombre(),
            room.getTipo().name(),
            room.getCantidadParticipantes(),
            room.getCreadorUsername()
        );
    }
}
