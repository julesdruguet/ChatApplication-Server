/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chatapplication_server.components.ClientSocketEngine;

import SocketActionMessages.ChatMessage;
import chatapplication_server.ComponentManager;
import chatapplication_server.components.ConfigManager;
import chatapplication_server.components.ServerSocketEngine.SocketConnectionHandler;
import chatapplication_server.components.ServerSocketEngine.SocketServerEngine;
import chatapplication_server.components.ServerSocketEngine.SocketServerGUI;
import chatapplication_server.components.base.GenericThreadedComponent;
import chatapplication_server.components.base.IComponent;
import chatapplication_server.exception.ComponentInitException;
import chatapplication_server.statistics.ServerStatistics;

import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;

import java.math.BigInteger;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Scanner;

import static chatapplication_server.components.ServerSocketEngine.SocketConnectionHandler.decrypt;
import static chatapplication_server.components.ServerSocketEngine.SocketConnectionHandler.encrypt;

/**
 * @author atgianne
 */
public class ClientEngine extends GenericThreadedComponent {
    /**
     * Instance of the ConfigManager component
     */
    ConfigManager configManager;

    /**
     * Object for printing the secure socket server configuration properties
     */
    ServerStatistics lotusStat;

    /**
     * Flag indicating whether the Socket Server is running....
     */
    boolean isRunning;

    /**
     * The Socket connection to the Chat Application Server
     */
    private Socket socket;

    /**
     * Socket Stream reader/writer that will be used throughout the whole connection...
     */
    private ObjectOutputStream socketWriter;
    private ObjectInputStream socketReader;

    /**
     * Singleton instance of the SocketServerEngine component
     */
    private static ClientEngine componentInstance = null;

    /**
     * Mutual key set with server
     */
    private SecretKeySpec symmetricKey;

    final String keyStore = "certs/BobKeyStore.jks"; // keystore file should exist in the program folder of the application
    final String keyStorePass = "123456"; // password of keystore
    final String keyPass = "123456";

    /**
     * Creates a new instance of SocketServerEngine
     */
    public ClientEngine() {
        isRunning = false;
    }

    /**
     * Make sure that we can only get one instance of the SocketServerEngine component.
     * Implementation of the static getInstance() method.
     */
    public static ClientEngine getInstance() {
        if (componentInstance == null)
            componentInstance = new ClientEngine();

        return componentInstance;
    }

    /**
     * Implementation of IComponent.initialize method().
     * This method is called upon initialize of the ClientEngine component and handles any configuration that needs to be
     * done in the client before it connects to the Chat Application Server.
     *
     * @see IComponent interface.
     */
    public void initialize() throws ComponentInitException {
        /** Get the running instance of the Configuration Manager component */
        configManager = ConfigManager.getInstance();

        /** For printing the configuration properties of the secure socket server */
        lotusStat = new ServerStatistics();

        /** Try and connect to the server... */
        try {
            socket = new Socket(configManager.getValue("Server.Address"), configManager.getValueInt("Server.PortNumber"));
        } catch (Exception e) {
            display("Error connecting to the server:" + e.getMessage() + "\n");
            ClientSocketGUI.getInstance().loginFailed();
            return;
        }

        /** Print that the connection was accepted */
        display("Connection accepted: " + socket.getInetAddress() + ":" + socket.getPort() + "\n");

        /** Create the read/write object streams... */
        try {
            /** Set up the stream reader/writer for this socket connection... */
            socketWriter = new ObjectOutputStream(socket.getOutputStream());
            socketReader = new ObjectInputStream(socket.getInputStream());

            /** Start the ListeFromServer thread... */
            new ListenFromServer().start();
        } catch (IOException ioe) {
            display("Exception creating new Input/Output Streams: " + ioe + "\n");
            ComponentManager.getInstance().fatalException(ioe);
        }

        /** Send our username to the server... */
        try {
            socketWriter.writeObject(configManager.getValue("Client.Username"));
        } catch (IOException ioe) {
            display("Exception during login: " + ioe);
            shutdown();
            ComponentManager.getInstance().fatalException(ioe);
        }

        String msg = "HELLO";
        sendMessage(new ChatMessage(ChatMessage.HELLO, msg));
        String encryptedMsg = null;

        try {
            encryptedMsg = (String) this.getStreamReader().readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        try {
            msg = decrypt(encryptedMsg, this.getServerPublicKey());
        } catch (FileNotFoundException | CertificateException e) {
            e.printStackTrace();
        }

        if (msg.equals("HELLO")) {
            /* If the server is authenticated, we prove to the server that we are who we say we are */
            /* when starting DH for symmetricKey agreement */
            java.security.KeyStore ks = null;
            try {
                ks = java.security.KeyStore.getInstance("JKS");
                java.io.FileInputStream ksfis = new java.io.FileInputStream(keyStore);
                java.io.BufferedInputStream ksbufin = new java.io.BufferedInputStream(ksfis);
                ks.load(ksbufin, keyStorePass.toCharArray());

                // list aliases in the keystore
                java.io.FileOutputStream fos = null;
                for (java.util.Enumeration theAliases = ks.aliases(); theAliases.hasMoreElements(); ) {
                    String alias = (String) theAliases.nextElement();
                    java.security.cert.Certificate cert = ks.getCertificate(alias);
                    java.security.PrivateKey privateKey = (java.security.PrivateKey) ks.getKey(alias, keyPass.toCharArray());
                }
            } catch (KeyStoreException | IOException | CertificateException | UnrecoverableKeyException | NoSuchAlgorithmException e) {
                e.printStackTrace();
            }

            // Generate the symmetric key
            SecureRandom random = new SecureRandom();
            byte[] keyBytes = new byte[16];
            random.nextBytes(keyBytes);
            this.symmetricKey = new SecretKeySpec(keyBytes, "AES");
            msg = symmetricKey.getEncoded().toString();
            try {
                encryptedMsg = encrypt(msg, this.getServerPublicKey().getEncoded());
            } catch (FileNotFoundException | CertificateException e) {
                e.printStackTrace();
            }
            sendMessage(new ChatMessage(ChatMessage.SYM_KEY, encryptedMsg));

            encryptedMsg = null;

            try {
                encryptedMsg = (String) this.getStreamReader().readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
            try {
                msg = decrypt(encryptedMsg, this.getServerPublicKey());
            } catch (FileNotFoundException | CertificateException e) {
                e.printStackTrace();
            }

        }

        super.initialize();
    }

    /**
     * Method for displaying a message in the Client GUI
     *
     * @msg The string message to be displayed
     */
    private void display(String msg) {
        ClientSocketGUI.getInstance().append(msg);
    }

    /**
     * Method for sending a message to the server
     *
     * @param msg The message to be sent
     */
    public void sendMessage(ChatMessage msg) {
        try {

            socketWriter.writeObject(msg);
        } catch (IOException e) {
            display("Exception writing to server: " + e);
        }
    }

    /**
     * Method holding the main logic of the Client Engine. It basically waits for inputs from the user to be sent to the Server.
     */
    public void componentMain() {
        while (!mustShutdown) {
            /** Wait messages from the user... */
            try {
                Thread.sleep(7000);
            } catch (InterruptedException ie) {

            }

            // read message from user
            //String msg = scan.nextLine();
            String readMsg = ClientSocketGUI.getInstance().getPublicMsgToBeSent();
            String msg = encrypt(readMsg, symmetricKey.getEncoded());

            if (msg.equals("")) {
                continue;
            } else {
                // logout if message is LOGOUT
                if (msg.equalsIgnoreCase("LOGOUT")) {
                    sendMessage(new ChatMessage(ChatMessage.LOGOUT, ""));
                    // break to do the disconnect
                    break;
                }
                // message WhoIsIn
                else if (msg.equalsIgnoreCase("WHOISIN")) {
                    sendMessage(new ChatMessage(ChatMessage.WHOISIN, ""));
                } else if (msg.equalsIgnoreCase("PRIVATEMESSAGE")) {                // default to ordinary message
                    sendMessage(new ChatMessage(ChatMessage.PRIVATEMESSAGE, msg));
                } else {                // default to ordinary message
                    sendMessage(new ChatMessage(ChatMessage.MESSAGE, msg));
                }
            }

            shutdown();
        }
    }

    public ObjectInputStream getStreamReader() {
        return socketReader;
    }

    public PublicKey getServerPublicKey() throws FileNotFoundException, CertificateException {
        FileInputStream fr = new FileInputStream("ca_root.cer");
        CertificateFactory cf = CertificateFactory.getInstance("X509");
        X509Certificate c = (X509Certificate) cf.generateCertificate(fr);
        return c.getPublicKey();
    }

    /**
     * Override GenericThreadedComponent.shutdown() method.
     * Signal and wait until the ClientEngine thread, holding the secure socket connection, stops.
     *
     * @see GenericThreadedComponent
     */
    public void shutdown() {
        /** Close the secure socket server */
        try {
            synchronized (socket) {
                /** Shut down the Client Socket */
                socketReader.close();
                socketWriter.close();
                socket.close();

                isRunning = false;


                /** Print in the Event area of the Server Windows GUI the close operation of the Socket Server... */
                ClientSocketGUI.getInstance().append("[CCEngine]:: Shutting down the Client Engine....COMPLETE (" + lotusStat.getCurrentDate() + ")\n");
            }
        } catch (Exception e) {
            /** Print to the logging stream that shutting down the Central System socket server failed */
            ClientSocketGUI.getInstance().append("[CCEngine]: Failed shutting down CS socket server -- " + e.getMessage() + " (" + lotusStat.getCurrentDate() + ")\n");
        }

        /** Invoke our parent's method to stop the thread running the secure socket server... */
        super.shutdown();
    }

    public SecretKeySpec getSymmetricKey() {
        return symmetricKey;
    }
}
