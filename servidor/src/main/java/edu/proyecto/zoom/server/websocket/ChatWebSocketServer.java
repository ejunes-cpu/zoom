package edu.proyecto.zoom.server.websocket;

import com.google.gson.Gson;
import edu.proyecto.zoom.server.model.Mensaje;
import edu.proyecto.zoom.server.model.TipoMensaje;
import edu.proyecto.zoom.server.model.Usuario;
import edu.proyecto.zoom.server.service.ConnectionManager;
import edu.proyecto.zoom.server.service.WebSocketRoomManager;
import edu.proyecto.zoom.server.strategy.*;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class ChatWebSocketServer extends WebSocketServer {

    private final Gson gson;
    private final ConnectionManager connectionManager;
    private final WebSocketRoomManager roomManager;
    private final Map<TipoMensaje, MessageStrategy> strategies;

    public ChatWebSocketServer(InetSocketAddress address) {
        super(address);
        this.gson = new Gson();
        this.connectionManager = ConnectionManager.getInstance();
        this.roomManager = WebSocketRoomManager.getInstance();
        
        // Initialize Strategies
        this.strategies = new HashMap<>();

        // ─── Estrategias existentes ───────────────────────────────────────────
        this.strategies.put(TipoMensaje.LOGIN, new LoginStrategy());
        this.strategies.put(TipoMensaje.MENSAJE, new PublicMessageStrategy());
        this.strategies.put(TipoMensaje.PRIVADO, new PrivateMessageStrategy());

        // ─── Nuevas estrategias de salas ──────────────────────────────────────
        this.strategies.put(TipoMensaje.CREAR_SALA, new CrearSalaStrategy());
        this.strategies.put(TipoMensaje.LISTA_SALAS, new ListaSalasStrategy());
        this.strategies.put(TipoMensaje.UNIRSE_SALA, new UnirseSalaStrategy());
        this.strategies.put(TipoMensaje.OBTENER_CODIGO, new ObtenerCodigoStrategy());
        this.strategies.put(TipoMensaje.SALIR_SALA, new SalirSalaStrategy());
        this.strategies.put(TipoMensaje.MSG_SALA, new MsgSalaStrategy());

        // ─── Estrategias de screen sharing ────────────────────────────────────
        ScreenShareStrategy screenShareStrategy = new ScreenShareStrategy();
        this.strategies.put(TipoMensaje.SCREEN_START, screenShareStrategy);
        this.strategies.put(TipoMensaje.SCREEN_STOP, screenShareStrategy);
        this.strategies.put(TipoMensaje.SCREEN_FRAME, new ScreenFrameStrategy());

        // ─── Estrategias de audio en tiempo real ──────────────────────────────
        AudioDataStrategy audioDataStrategy = new AudioDataStrategy();
        this.strategies.put(TipoMensaje.AUDIO_START, audioDataStrategy);
        this.strategies.put(TipoMensaje.AUDIO_STOP, audioDataStrategy);
        this.strategies.put(TipoMensaje.AUDIO_DATA, audioDataStrategy);

        // Comandos y otras funcionalidades pueden agregarse aqui
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Nueva conexión entrante: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Conexión cerrada: " + conn.getRemoteSocketAddress());

        // Obtener usuario antes de removerlo para limpiar salas
        Usuario usuario = connectionManager.getUsuario(conn);
        if (usuario != null) {
            roomManager.desconectarUsuario(usuario.getNombre());
        }

        connectionManager.removeUsuario(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            Mensaje msg = gson.fromJson(message, Mensaje.class);
            MessageStrategy strategy = strategies.get(msg.getTipo());
            
            if (strategy != null) {
                strategy.processMessage(msg, conn);
            } else {
                System.out.println("Tipo de mensaje no soportado: " + msg.getTipo());
            }
        } catch (Exception e) {
            System.err.println("Error procesando mensaje: " + e.getMessage());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("Error en conexión " + (conn != null ? conn.getRemoteSocketAddress() : "desconocida") + ": " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("Servidor WebSocket iniciado en el puerto: " + getPort());
    }
}
