/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chat2;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clase principal del servidor de chat. Escucha conexiones entrantes y lanza un
 * hilo por cada cliente conectado.
 */
public class Servidor {

    // Lista concurrente de usuarios conectados
    public static final Set<Flujo> usuarios = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static final Set<String> usuariosBloqueados = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Mapa concurrente de salas privadas, cada sala tiene un conjunto sincronizado de usuarios (nombres)
    public static final Map<String, Set<String>> salas = new ConcurrentHashMap<>();
    public static JFramePanelControl panelControl;

    public static void main(String[] args) {
        panelControl = new JFramePanelControl();
        panelControl.setVisible(true);
        try (ServerSocket server = new ServerSocket(8000)) {
            System.out.println("Servidor Activo en puerto 8000...");

            while (true) {
                Socket socket = server.accept();
                Flujo flujo = new Flujo(socket);
                flujo.start();
            }
        } catch (IOException ioe) {
            System.err.println("Error en el servidor:  " + ioe.getMessage());
        }
    }

    /**
     * Método para mostrar los nombres de los usuarios conectados. Útil para
     * administración y debugging.
     */
    public static void mostrarUsuarios() {
        System.out.println("Usuarios conectados:");
        for (Flujo f : usuarios) {
            System.out.println("Nombre: " + f.getNombre());
        }
    }
}
