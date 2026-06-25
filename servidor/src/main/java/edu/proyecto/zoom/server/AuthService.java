package edu.proyecto.zoom.server;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;   // Disponible desde Java 17
import java.util.Map;

/**
 * Servicio de autenticación simple con contraseñas hasheadas en SHA-256.
 *
 * Usa {@link HexFormat#of().formatHex(byte[])} introducido en Java 17
 * para convertir el digest binario a su representación hexadecimal.
 *
 * NOTA PARA PRODUCCIÓN: No usar SHA-256 plano para contraseñas.
 * Emplear PBKDF2WithHmacSHA256 o bcrypt con salt individual por usuario
 * para proteger contra ataques de diccionario y rainbow tables.
 *
 * Usuarios disponibles en este prototipo:
 *   - ana   / 123456
 *   - luis  / 123456
 *   - host  / admin123
 */
public final class AuthService {

    /**
     * Instancia de HexFormat reutilizable (thread-safe e inmutable).
     * HexFormat.of() devuelve letras hexadecimales en minúsculas sin separador.
     */
    private static final HexFormat HEX = HexFormat.of();

    /**
     * Mapa de usuario → hash SHA-256 de su contraseña.
     * Se inicializa una sola vez al cargar la clase (thread-safe por ser final e inmutable).
     *
     * Los hashes se calculan en tiempo de carga con sha256() para no
     * almacenar contraseñas en texto plano ni en el binario compilado.
     */
    private static final Map<String, String> USUARIOS = Map.of(
        "ana",  sha256("123456"),
        "luis", sha256("123456"),
        "host", sha256("admin123")
    );

    // ─── API pública ───────────────────────────────────────────────────────────

    /**
     * Valida las credenciales de un usuario comparando el hash SHA-256
     * de la contraseña proporcionada con el hash almacenado en el mapa.
     *
     * @param username nombre de usuario (sensible a mayúsculas)
     * @param password contraseña en texto plano
     * @return {@code true} si las credenciales son correctas, {@code false} en caso contrario
     */
    public static boolean validate(String username, String password) {
        // Buscar el hash esperado; null si el usuario no existe
        String hashEsperado = USUARIOS.get(username);

        // Rechazo rápido si el usuario no está registrado
        if (hashEsperado == null) {
            return false;
        }

        // Comparar hash almacenado con hash calculado de la contraseña ingresada
        return hashEsperado.equals(sha256(password));
    }

    // ─── Implementación interna ────────────────────────────────────────────────

    /**
     * Calcula el hash SHA-256 de un texto y lo devuelve como String hexadecimal
     * en minúsculas usando {@link HexFormat#formatHex(byte[])} (Java 17+).
     *
     * Ejemplo: sha256("123456") →
     *   "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92"
     *
     * NOTA PARA PRODUCCIÓN: Reemplazar con PBKDF2 o bcrypt + salt individual.
     *
     * @param text texto a hashear (se codifica en UTF-8)
     * @return representación hexadecimal del hash SHA-256
     * @throws RuntimeException si SHA-256 no está disponible (imposible en JDK estándar)
     */
    private static String sha256(String text) {
        try {
            // Obtener la instancia del algoritmo SHA-256 del JDK
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Calcular el digest sobre los bytes UTF-8 del texto
            byte[] hashBytes = digest.digest(
                text.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            // Convertir el array de bytes a su representación hexadecimal (Java 17+)
            // HexFormat.of().formatHex() produce letras minúsculas sin separadores
            return HEX.formatHex(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            // SHA-256 siempre está disponible en cualquier JDK estándar; no debería ocurrir
            throw new RuntimeException("SHA-256 no disponible en este JDK", e);
        }
    }

    // Constructor privado: clase utilitaria, no instanciable
    private AuthService() {}
}
