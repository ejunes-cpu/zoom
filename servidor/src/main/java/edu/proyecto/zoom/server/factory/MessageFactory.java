package edu.proyecto.zoom.server.factory;

import edu.proyecto.zoom.server.model.Mensaje;
import edu.proyecto.zoom.server.model.TipoMensaje;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MessageFactory {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static Mensaje createSystemMessage(String contenido) {
        return new Mensaje(TipoMensaje.SISTEMA, "Servidor", "Todos", contenido, getCurrentTime());
    }

    public static Mensaje createErrorMessage(String error, String destinatario) {
        return new Mensaje(TipoMensaje.ERROR, "Servidor", destinatario, error, getCurrentTime());
    }

    public static Mensaje createUsersMessage(String userListJson, String destinatario) {
        return new Mensaje(TipoMensaje.USUARIOS, "Servidor", destinatario, userListJson, getCurrentTime());
    }

    private static String getCurrentTime() {
        return LocalDateTime.now().format(formatter);
    }
}
