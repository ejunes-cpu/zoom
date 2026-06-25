package edu.proyecto.zoom.server.model;

import org.java_websocket.WebSocket;

public class Usuario {
    private String id;
    private String nombre;
    private WebSocket conexion;

    public Usuario(String id, String nombre, WebSocket conexion) {
        this.id = id;
        this.nombre = nombre;
        this.conexion = conexion;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public WebSocket getConexion() {
        return conexion;
    }

    public void setConexion(WebSocket conexion) {
        this.conexion = conexion;
    }
}
