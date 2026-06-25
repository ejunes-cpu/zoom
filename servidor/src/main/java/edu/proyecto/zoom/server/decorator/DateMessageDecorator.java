package edu.proyecto.zoom.server.decorator;

import edu.proyecto.zoom.server.model.Mensaje;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateMessageDecorator implements MessageDecorator {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public Mensaje decorate(Mensaje mensaje) {
        if (mensaje.getFecha() == null || mensaje.getFecha().isEmpty()) {
            mensaje.setFecha(LocalDateTime.now().format(formatter));
        }
        return mensaje;
    }
}
