package edu.proyecto.zoom.server.service;

import edu.proyecto.zoom.server.model.Usuario;
import edu.proyecto.zoom.server.observer.ConnectionObservable;
import edu.proyecto.zoom.server.observer.ConnectionObserver;
import org.java_websocket.WebSocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager implements ConnectionObservable {

    private static ConnectionManager instance;
    private final Map<WebSocket, Usuario> usuarios;
    private final List<ConnectionObserver> observers;

    private ConnectionManager() {
        this.usuarios = new ConcurrentHashMap<>();
        this.observers = new ArrayList<>();
    }

    public static synchronized ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    public void addUsuario(WebSocket conn, Usuario usuario) {
        usuarios.put(conn, usuario);
        notifyUserConnected(usuario);
    }

    public void removeUsuario(WebSocket conn) {
        Usuario u = usuarios.remove(conn);
        if (u != null) {
            notifyUserDisconnected(u);
        }
    }

    public Usuario getUsuario(WebSocket conn) {
        return usuarios.get(conn);
    }

    public Usuario getUsuarioByNombre(String nombre) {
        for (Usuario u : usuarios.values()) {
            if (u.getNombre().equals(nombre)) {
                return u;
            }
        }
        return null;
    }

    public List<Usuario> getAllUsuarios() {
        return new ArrayList<>(usuarios.values());
    }

    @Override
    public void addObserver(ConnectionObserver observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(ConnectionObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyUserConnected(Usuario usuario) {
        for (ConnectionObserver obs : observers) {
            obs.onUserConnected(usuario);
        }
    }

    @Override
    public void notifyUserDisconnected(Usuario usuario) {
        for (ConnectionObserver obs : observers) {
            obs.onUserDisconnected(usuario);
        }
    }
}
