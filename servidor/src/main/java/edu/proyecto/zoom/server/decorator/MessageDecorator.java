package edu.proyecto.zoom.server.decorator;

import edu.proyecto.zoom.server.model.Mensaje;

public interface MessageDecorator {
    Mensaje decorate(Mensaje mensaje);
}
