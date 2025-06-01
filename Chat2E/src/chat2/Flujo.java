/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chat2;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clase que representa al flujo de comunicación entre servidor y cliente. Cada
 * instancia se ejecuta con un hilo diferente.
 */
public class Flujo extends Thread {

    public Socket socket;
    public DataInputStream entrada;
    public DataOutputStream salida;
    public String nombre;

    // Usuarios bloqueados por este cliente
    private final Set<String> blockedUsers = new HashSet<>();
    public static final String FILE_PREFIX = "__FILE__";

    public Flujo(Socket socket) {
        this.socket = socket;
        try {
            // Inicializamos los streams directamente sin buffers extras
            entrada = new DataInputStream(socket.getInputStream());
            salida = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            System.out.println("Error en constructor Flujo: " + e.getMessage());
            cerrarConexion();
        }
    }

    public String getNombre() {
        return nombre;
    }

    @Override
    public void run() {
        try {
            nombre = entrada.readUTF();
            synchronized (Servidor.usuarios) {
                Servidor.usuarios.add(this);
            }
            if (Servidor.panelControl != null) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    Servidor.panelControl.inicialiceLista();
                });
            }

            enviarListaUsuarios();
            broadcast("message:" + nombre + " se ha conectado.");

            while (!socket.isClosed()) {
                String linea = entrada.readUTF();
                procesarComando(linea);
            }

        } catch (IOException e) {
            System.out.println("Conexión cerrada con " + nombre + ": " + e.getMessage());
        } finally {
            cerrarConexion();
            synchronized (Servidor.usuarios) {
                Servidor.usuarios.remove(this);
            }
            if (Servidor.panelControl != null) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    Servidor.panelControl.inicialiceLista();
                });
            }

            broadcast("message:" + nombre + " se ha desconectado.");
            enviarListaUsuarios();
        }
    }

    private void procesarComando(String linea) throws IOException {
        System.out.println("Comando recibido de " + nombre + ": " + linea);

        if (linea.startsWith("/block ")) {
            bloquearUsuario(linea.substring(7));
        } else if (linea.startsWith("/unblock ")) {
            desbloquearUsuario(linea.substring(9));
        } else if (linea.startsWith("/create ")) {
            crearSala(linea.substring(8));
        } else if (linea.startsWith("/join ")) {
            unirseSala(linea.substring(6));
        } else if (linea.startsWith("/leave ")) {
            salirSala(linea.substring(7));
        } else if (linea.startsWith("private:")) {
            enviarMensajePrivado(linea);
        } else if (linea.startsWith(FILE_PREFIX)) {
            recibirArchivo(linea);
        } else if (linea.startsWith("#")) {
            enviarMensajeSala(linea);
        } else if (linea.startsWith("message:")) {
            broadcast(linea);
        } else {
            salida.writeUTF("message:Comando no reconocido.");
            salida.flush();
        }
    }

    private void bloquearUsuario(String user) throws IOException {
        blockedUsers.add(user);
        salida.writeUTF("message:Has bloqueado a " + user);
        salida.flush();
    }

    private void desbloquearUsuario(String user) throws IOException {
        blockedUsers.remove(user);
        salida.writeUTF("message:Has desbloqueado a " + user);
        salida.flush();
    }

    private void crearSala(String room) {
        Servidor.salas.putIfAbsent(room, Collections.synchronizedSet(new HashSet<>()));
        Servidor.salas.get(room).add(nombre);
    }

    private void unirseSala(String room) {
        Servidor.salas.putIfAbsent(room, Collections.synchronizedSet(new HashSet<>()));
        Servidor.salas.get(room).add(nombre);
    }

    private void salirSala(String room) {
        Set<String> miembros = Servidor.salas.get(room);
        if (miembros != null) {
            miembros.remove(nombre);
        }
    }

    private void recibirArchivo(String header) throws IOException {
        String[] partes = header.split(":");
        if (partes.length < 4) {
            return;
        }

        String destinatario = partes[1];
        String nombreArchivo = partes[2];
        long tam = Long.parseLong(partes[3]);

        byte[] buffer = new byte[(int) tam];
        entrada.readFully(buffer);

        synchronized (Servidor.usuarios) {
            for (Flujo f : Servidor.usuarios) {
                if (f.getNombre().equals(destinatario) && !f.blockedUsers.contains(this.nombre)) {
                    f.salida.writeUTF(FILE_PREFIX + ":" + this.nombre + ":" + nombreArchivo + ":" + tam);
                    f.salida.write(buffer);
                    f.salida.flush();
                    break;
                }
            }
        }
    }

    public void enviarMensajePrivado(String linea) throws IOException {
        String[] partes = linea.split(":", 4);
        if (partes.length < 4) {
            salida.writeUTF("message:Formato de mensaje privado incorrecto.");
            salida.flush();
            return;
        }

        String remitente = partes[1].trim();
        String destinatario = partes[2].trim();
        String mensaje = partes[3].trim();

        boolean encontrado = false;

        synchronized (Servidor.usuarios) {
            for (Flujo f : Servidor.usuarios) {
                if (f.getNombre().equals(destinatario) && !f.blockedUsers.contains(remitente)) {
                    f.salida.writeUTF("private:" + remitente + ": " + mensaje);
                    f.salida.flush();
                    encontrado = true;
                    break;
                }
            }
        }

        if (!encontrado) {
            salida.writeUTF("message:El usuario \"" + destinatario + "\" no está conectado o te ha bloqueado.");
            salida.flush();
        }
    }

    public void enviarMensajeSala(String linea) throws IOException {
        int sep = linea.indexOf(":");
        if (sep == -1) {
            return;
        }

        String room = linea.substring(1, sep);
        String mensaje = linea.substring(sep + 1);

        Set<String> miembros = Servidor.salas.get(room);
        if (miembros != null && miembros.contains(nombre)) {
            synchronized (Servidor.usuarios) {
                for (Flujo f : Servidor.usuarios) {
                    if (miembros.contains(f.getNombre()) && !f.blockedUsers.contains(nombre)) {
                        f.salida.writeUTF("message:(Sala #" + room + ") " + nombre + ": " + mensaje);
                        f.salida.flush();
                    }
                }
            }
        }
    }

    public void broadcast(String mensaje) {
        synchronized (Servidor.usuarios) {
            for (Flujo f : Servidor.usuarios) {
                if (!f.blockedUsers.contains(this.nombre)) {
                    try {
                        f.salida.writeUTF(mensaje);
                        f.salida.flush();
                    } catch (IOException e) {
                        System.out.println("Error en broadcast con usuario " + f.getNombre() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    public void enviarListaUsuarios() {
        StringBuilder lista = new StringBuilder("[USERS],");
        synchronized (Servidor.usuarios) {
            for (Flujo f : Servidor.usuarios) {
                lista.append(f.getNombre()).append(",");
            }
        }
        if (lista.length() > 7) {
            lista.setLength(lista.length() - 1);
        }
        broadcast(lista.toString());
    }

    private void cerrarConexion() {
        try {
            if (entrada != null) {
                entrada.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (salida != null) {
                salida.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
