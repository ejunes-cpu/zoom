package edu.proyecto.zoom.server.observer;

import edu.proyecto.zoom.server.model.Usuario;

public interface ConnectionObserver {
    void onUserConnected(Usuario usuario);
    void onUserDisconnected(Usuario usuario);
}
