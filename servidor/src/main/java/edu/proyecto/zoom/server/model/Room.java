package edu.proyecto.zoom.server.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modelo de una sala de videoconferencia.
 *
 * Soporta dos tipos de acceso:
 *   - LIBRE: cualquier usuario puede unirse sin restricción
 *   - PRIVADA: requiere un código de 6 dígitos generado automáticamente
 *
 * El código de acceso NUNCA se expone en las listas públicas de salas.
 * Solo el creador puede consultar el código mediante una solicitud explícita.
 */
public class Room {

    /** Tipos de acceso a la sala */
    public enum RoomType { LIBRE, PRIVADA }

    private final String roomId;
    private final String nombre;
    private final RoomType tipo;
    private final String codigoAcceso;       // Solo para PRIVADA
    private final String creadorUsername;
    private final Set<String> participantes;  // Usernames conectados (thread-safe)
    private final LocalDateTime creadaEn;
    private boolean activa;

    // ─── Estado de screen sharing ──────────────────────────────────────────────
    /** Username del usuario que está compartiendo pantalla (null si nadie) */
    private volatile String compartidorPantalla;

    /**
     * Crea una nueva sala.
     *
     * @param roomId           identificador único (UUID)
     * @param nombre           nombre visible de la sala
     * @param tipo             LIBRE o PRIVADA
     * @param creadorUsername   username del creador
     * @param customCodigo     código de acceso personalizado (si es null se genera uno para privadas)
     */
    public Room(String roomId, String nombre, RoomType tipo, String creadorUsername, String customCodigo) {
        this.roomId          = roomId;
        this.nombre          = nombre;
        this.tipo            = tipo;
        this.creadorUsername  = creadorUsername;
        this.participantes   = ConcurrentHashMap.newKeySet();
        this.creadaEn        = LocalDateTime.now();
        this.activa          = true;
        this.compartidorPantalla = null;

        // Generar código solo para salas privadas si no se provee uno
        if (tipo == RoomType.PRIVADA) {
            this.codigoAcceso = (customCodigo != null && !customCodigo.isEmpty()) ? customCodigo : String.format("%06d", new Random().nextInt(999999));
        } else {
            this.codigoAcceso = null;
        }

        // El creador se une automáticamente como primer participante
        this.participantes.add(creadorUsername);
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public String getRoomId()          { return roomId; }
    public String getNombre()          { return nombre; }
    public RoomType getTipo()          { return tipo; }
    public String getCodigoAcceso()    { return codigoAcceso; }
    public String getCreadorUsername()  { return creadorUsername; }
    public LocalDateTime getCreadaEn() { return creadaEn; }
    public boolean isActiva()          { return activa; }

    /** Devuelve una vista no modificable de los participantes */
    public Set<String> getParticipantes() {
        return Collections.unmodifiableSet(participantes);
    }

    public int getCantidadParticipantes() {
        return participantes.size();
    }

    // ─── Gestión de participantes ─────────────────────────────────────────────

    public boolean agregarParticipante(String username) {
        participantes.add(username);
        return true;
    }

    public boolean removerParticipante(String username) {
        boolean removido = participantes.remove(username);
        // Si el compartidor de pantalla se va, detener el share
        if (username.equals(compartidorPantalla)) {
            compartidorPantalla = null;
        }
        return removido;
    }

    public boolean contieneParticipante(String username) {
        return participantes.contains(username);
    }

    // ─── Control de sala ──────────────────────────────────────────────────────

    public void desactivar() {
        this.activa = false;
    }

    /** Verifica si el usuario dado es el creador de la sala */
    public boolean esCreador(String username) {
        return creadorUsername.equals(username);
    }

    /** Valida el código de acceso para salas privadas */
    public boolean validarCodigo(String codigo) {
        if (tipo != RoomType.PRIVADA) return true; // Salas libres no requieren código
        return codigoAcceso != null && codigoAcceso.equals(codigo);
    }

    // ─── Screen Sharing ───────────────────────────────────────────────────────

    public String getCompartidorPantalla() {
        return compartidorPantalla;
    }

    public void setCompartidorPantalla(String username) {
        this.compartidorPantalla = username;
    }

    public boolean alguienCompartiendo() {
        return compartidorPantalla != null;
    }
}
