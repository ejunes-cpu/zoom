package edu.proyecto.zoom.server.observer;

public interface ConnectionObservable {
    void addObserver(ConnectionObserver observer);
    void removeObserver(ConnectionObserver observer);
    void notifyUserConnected(edu.proyecto.zoom.server.model.Usuario usuario);
    void notifyUserDisconnected(edu.proyecto.zoom.server.model.Usuario usuario);
}
