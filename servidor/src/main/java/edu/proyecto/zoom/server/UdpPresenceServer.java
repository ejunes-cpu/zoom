package edu.proyecto.zoom.server;

import edu.proyecto.zoom.model.Protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servidor UDP para recibir heartbeats de presencia de los clientes.
 *
 * Los clientes envían paquetes UDP periódicamente con el formato:
 *   HEARTBEAT|usuario|sala|timestamp
 *
 * Este servidor los recibe, los imprime por consola y podría extenderse
 * para detectar clientes caídos si dejan de enviar heartbeats.
 *
 * Implementa Runnable para poder ejecutarse en un hilo separado.
 */
public class UdpPresenceServer implements Runnable {

    private static final Logger LOG = Logger.getLogger(UdpPresenceServer.class.getName());

    /** Tamaño máximo del buffer de recepción UDP (1024 bytes) */
    private static final int BUFFER_SIZE = 1024;

    /** Socket UDP que escucha en el puerto definido en Protocol */
    private DatagramSocket socket;

    /** Bandera para detener el bucle de escucha limpiamente */
    private volatile boolean corriendo = true;

    /**
     * Punto de entrada del hilo UDP.
     * Abre el DatagramSocket y entra en un bucle infinito de recepción.
     */
    @Override
    public void run() {
        // Intentar abrir el socket UDP en el puerto configurado
        try {
            socket = new DatagramSocket(Protocol.UDP_PORT);
            LOG.info("UDP Presence Server escuchando en puerto " + Protocol.UDP_PORT);
        } catch (SocketException e) {
            LOG.log(Level.SEVERE, "No se pudo abrir el socket UDP en puerto " + Protocol.UDP_PORT, e);
            return; // No tiene sentido continuar sin socket
        }

        // Buffer reutilizable para los paquetes entrantes
        byte[] buffer = new byte[BUFFER_SIZE];

        // Bucle principal de recepción — corre hasta que corriendo sea false
        while (corriendo) {
            try {
                // Crear el paquete contenedor y esperar datos (bloqueante)
                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                socket.receive(paquete);

                // Convertir los bytes recibidos a String UTF-8
                String mensaje = new String(
                    paquete.getData(),
                    0,
                    paquete.getLength(),
                    java.nio.charset.StandardCharsets.UTF_8
                );

                // Obtener la IP del remitente para logging
                String remitente = paquete.getAddress().getHostAddress();

                // Imprimir el heartbeat recibido
                // Formato esperado: HEARTBEAT|usuario|sala|timestamp
                System.out.printf("[UDP] %s → %s%n", remitente, mensaje);

            } catch (IOException e) {
                // Si el socket fue cerrado intencionalmente, no es un error
                if (corriendo) {
                    LOG.log(Level.WARNING, "Error recibiendo paquete UDP", e);
                }
            }
        }

        LOG.info("UDP Presence Server detenido.");
    }

    /**
     * Detiene el servidor UDP de forma limpia cerrando el socket,
     * lo que desbloquea el receive() bloqueante.
     */
    public void detener() {
        corriendo = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
