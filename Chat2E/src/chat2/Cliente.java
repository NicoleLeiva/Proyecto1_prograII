/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package chat2;

import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Cliente extends JFrame implements ActionListener {

    static Socket socket;
    static DataInputStream entrada;
    static DataOutputStream salida;
    static JTextField salidaTexto;
    static JTextArea entradaTexto;
    static JList<String> jList;
    static DefaultListModel<String> listModel;
    static String name;

    public Cliente(String nombreUsuario) {
        name = nombreUsuario;
        setTitle("Chat - " + name);
        setSize(500, 300);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        salidaTexto = new JTextField();
        salidaTexto.addActionListener(this);
        entradaTexto = new JTextArea();
        entradaTexto.setEditable(false);
        entradaTexto.setLineWrap(true);
        entradaTexto.setWrapStyleWord(true);

        listModel = new DefaultListModel<>();
        jList = new JList<>(listModel);
        jList.setPreferredSize(new Dimension(120, 0));

        add(new JScrollPane(jList), BorderLayout.WEST);
        add(salidaTexto, BorderLayout.SOUTH);
        add(new JScrollPane(entradaTexto), BorderLayout.CENTER);

        // Cerrar socket al cerrar ventana
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                try {
                    if (socket != null) {
                        socket.close();
                    }
                } catch (IOException ex) {
                    System.out.println("Error al cerrar socket: " + ex.getMessage());
                }
            }
        });

        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                String nombre = JOptionPane.showInputDialog(null, "Ingrese su nombre:");
                if (nombre == null || nombre.trim().isEmpty()) {
                    System.exit(0);
                }

                Cliente cliente = new Cliente(nombre);
                socket = new Socket("localhost", 8000);
                entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                salida = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                salida.writeUTF(nombre);
                salida.flush();

                new Thread(() -> {
                    try {
                        while (true) {
                            String linea = entrada.readUTF();
                            SwingUtilities.invokeLater(() -> {
                                if (linea.startsWith("private:")) {
                                    cliente.entradaTexto.append("[Privado] " + linea.substring(8) + "\n");
                                } else if (linea.startsWith("message:")) {
                                    cliente.entradaTexto.append(linea.substring(8) + "\n");
                                } else if (linea.startsWith("[USERS]")) {
                                    listModel.clear();
                                    for (String user : linea.substring(8).split(",")) {
                                        listModel.addElement(user);
                                    }
                                } else if (linea.startsWith("__FILE__")) {
                                    // Aquí puedes implementar recepción de archivos
                                    cliente.entradaTexto.append("[Archivo recibido]\n");
                                }
                            });
                        }
                    } catch (IOException e) {
                        SwingUtilities.invokeLater(() -> {
                            cliente.entradaTexto.append("Error en la conexión\n");
                        });
                    }
                }).start();

            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Error al conectar: " + e.getMessage());
                System.exit(1);
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String texto = salidaTexto.getText().trim();
        if (texto.isEmpty()) {
            return;
        }
        salidaTexto.setText("");
        try {
            if (texto.startsWith("/sendfile ")) {
                String[] partes = texto.split(" ", 3);
                if (partes.length < 3) {
                    entradaTexto.append("Uso correcto: /sendfile destinatario rutaArchivo\n");
                    return;
                }
                String destinatario = partes[1];
                File archivo = new File(partes[2]);
                if (!archivo.exists()) {
                    entradaTexto.append("Archivo no encontrado: " + partes[2] + "\n");
                    return;
                }
                FileInputStream fis = new FileInputStream(archivo);
                byte[] buffer = fis.readAllBytes();
                fis.close();

                salida.writeUTF("__FILE__:" + destinatario + ":" + archivo.getName() + ":" + buffer.length);
                salida.write(buffer);
            } else if (texto.startsWith("@")) {
                // Ejemplo: @Carlos Hola, ¿cómo estás?
                int espacio = texto.indexOf(" ");
                if (espacio != -1) {
                    String destinatario = texto.substring(1, espacio);
                    String mensajePrivado = texto.substring(espacio + 1);
                    
                    // Formato: private:remitente:destinatario:mensaje
                    entradaTexto.append("[Privado a " + destinatario + "] " + mensajePrivado + "\n");
                    salida.writeUTF("private:" + name + ":" + destinatario + ":" + mensajePrivado);
                    salida.flush();
                } else {
                    entradaTexto.append("Formato inválido. Usa: @usuario mensaje\n");
                }
            } else {
                salida.writeUTF("message:" + name + " : " + texto);
            }
            salida.flush();
        } catch (IOException ex) {
            entradaTexto.append("Error al enviar mensaje\n" + ex.getMessage() + "\n");
        }
    }
}
