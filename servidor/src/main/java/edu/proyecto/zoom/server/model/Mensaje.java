package edu.proyecto.zoom.server.model;

public class Mensaje {
    private TipoMensaje tipo;
    private String usuario;
    private String destinatario;
    private String contenido;
    private String fecha;

    public Mensaje() {
    }

    public Mensaje(TipoMensaje tipo, String usuario, String destinatario, String contenido, String fecha) {
        this.tipo = tipo;
        this.usuario = usuario;
        this.destinatario = destinatario;
        this.contenido = contenido;
        this.fecha = fecha;
    }

    public TipoMensaje getTipo() {
        return tipo;
    }

    public void setTipo(TipoMensaje tipo) {
        this.tipo = tipo;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public void setDestinatario(String destinatario) {
        this.destinatario = destinatario;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    @Override
    public String toString() {
        return "Mensaje{" +
                "tipo=" + tipo +
                ", usuario='" + usuario + '\'' +
                ", destinatario='" + destinatario + '\'' +
                ", contenido='" + contenido + '\'' +
                ", fecha='" + fecha + '\'' +
                '}';
    }
}
