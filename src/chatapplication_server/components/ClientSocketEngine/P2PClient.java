/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chatapplication_server.components.ClientSocketEngine;

import SocketActionMessages.ChatMessage;
import chatapplication_server.components.ConfigManager;
import chatapplication_server.components.ServerSocketEngine.SocketConnectionHandler;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import java.lang.Math;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Random;

import static chatapplication_server.components.ServerSocketEngine.SocketConnectionHandler.decrypt;
import static chatapplication_server.components.ServerSocketEngine.SocketConnectionHandler.encrypt;


/**
 *
 * @author atgianne
 */
public class P2PClient extends JFrame implements ActionListener
{
    private String host;
    private String port;
    private final JTextField tfServer;
    private final JTextField tfPort;
    private final JTextField tfsPort;
    private final JLabel label;
    private final JTextField tf;
    private final JTextArea ta;
    protected boolean keepGoing;
    JButton send, start;
    BigInteger a, b, p, g, A, B, K;
    private byte[] byteKey;

    P2PClient(){
        super("P2P Client Chat");
        host=ConfigManager.getInstance().getValue( "Server.Address" );
        port=ConfigManager.getInstance().getValue( "Server.PortNumber" );

        // The NorthPanel with:
        JPanel northPanel = new JPanel(new GridLayout(3,1));
        // the server name anmd the port number
        JPanel serverAndPort = new JPanel(new GridLayout(1,5, 1, 3));
        // the two JTextField with default value for server address and port number
        tfServer = new JTextField(host);
        tfPort = new JTextField("" + port);
        tfPort.setHorizontalAlignment(SwingConstants.RIGHT);

        tfsPort=new JTextField(5);
        tfsPort.setHorizontalAlignment(SwingConstants.RIGHT);
        start=new JButton("Start");
        start.addActionListener(this);

        serverAndPort.add(new JLabel("Receiver's Port No:  "));
        serverAndPort.add(tfPort);
        serverAndPort.add(new JLabel("Receiver's IP Add:  "));
        serverAndPort.add(tfServer);
        serverAndPort.add(new JLabel(""));
        // adds the Server an port field to the GUI
        northPanel.add(serverAndPort);

        // the Label and the TextField
        label = new JLabel("Enter message below", SwingConstants.LEFT);
        northPanel.add(label);
        tf = new JTextField();
        tf.setBackground(Color.WHITE);
        northPanel.add(tf);
        add(northPanel, BorderLayout.NORTH);

        // The CenterPanel which is the chat room
        ta = new JTextArea(" ", 80, 80);
        JPanel centerPanel = new JPanel(new GridLayout(1,1));
        centerPanel.add(new JScrollPane(ta));
        ta.setEditable(false);

//        ta2 = new JTextArea(80,80);
//        ta2.setEditable(false);
//        centerPanel.add(new JScrollPane(ta2));
        add(centerPanel, BorderLayout.CENTER);


        send = new JButton("Send");
        send.addActionListener(this);
        JPanel southPanel = new JPanel();
        southPanel.add(send);
        southPanel.add(start);
        JLabel lbl=new JLabel("Sender's Port No:");
        southPanel.add(lbl);
        tfsPort.setText("0");
        southPanel.add(tfsPort);
        add(southPanel, BorderLayout.SOUTH);

        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

//        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 600);
        setVisible(true);
        tf.requestFocus();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object o = e.getSource();
        if(o == send){
            if ( tfPort.getText().equals( ConfigManager.getInstance().getValue( "Server.PortNumber" ) ) )
            {
                display( "Cannot give the same port number as the Chat Application Server - Please give the port number of the peer client to communicate!\n" );
                return;
            }
            this.send(encrypt(tf.getText(), byteKey));
        }
        if(o == start){
            new ListenFromClient().start();
            p = new BigInteger("0f52e524f5fa9ddcc6abe604e420898ab4bf27b54a9557a106e73073835ec92311ed4245ac49d3e3f33473c57d003c866374e07597841d0b11da04d0fe4fb037df57222e9642e07cd75e4629afb1f481affc9aeffa899e0afb16e38f01a2c8ddb44712f82909136e9da8f95d08003a8ca7ff6ccfe37c3b6bb426ccda89930173a8553e5b77258f27a3f1bf7a731f85960c4514c106b71c75aa10bc8698754470d10f20f4ac4cb388161c7ea327e4ade1a1854f1a220d0542736945c92ff7c248e3ce9d745853e7a78218d93dafab409faa4c780ac3242ddb12a954e54787ac52fee83d0b56ed9c9fff39e5e5bf62324208ae6aed880eb31a4cd308e4c4aa2cccb137a5c1a9647eebf9d3f51528fe2ee27ffed9b938425703",16);
            g = new BigInteger("2");
            this.send("__KEY_EXCHANGE_1__p:" + String.valueOf(p) + "g:" + String.valueOf(g) + ";");
        }
    }

    public void display(String str) {
        ta.append(str + "\n");
        ta.setCaretPosition(ta.getText().length() - 1);
    }

    public boolean send(String str){
        Socket socket;
        ObjectOutputStream sOutput;		// to write on the socket
        // try to connect to the server
        try {
            socket = new Socket(tfServer.getText(), Integer.parseInt(tfPort.getText()));
        }
        // if it failed not much I can so
        catch(Exception ec) {
            display("Error connecting to server:" + ec.getMessage() + "\n");
            return false;
        }

        /* Creating both Data Stream */
        try
        {
//			sInput  = new ObjectInputStream(socket.getInputStream());
            sOutput = new ObjectOutputStream(socket.getOutputStream());
        }
        catch (IOException eIO) {
            display("Exception creating new Input/output Streams: " + eIO);
            return false;
        }

        try {
            sOutput.writeObject(new ChatMessage(str.length(), str));
            if(!str.startsWith("__KEY_EXCHANGE_")){
                display("You: " + decrypt(str, byteKey));
            } else {
                display("Key exchange...");
            }
            sOutput.close();
            socket.close();
        } catch (IOException ex) {
            display("Exception creating new Input/output Streams: " + ex);
        }

        return true;
    }

    private class ListenFromClient extends Thread{
        public ListenFromClient() {
            keepGoing=true;
        }

        @Override
        public void run() {
            try
            {
                // the socket used by the server
                ServerSocket serverSocket = new ServerSocket(Integer.parseInt(tfsPort.getText()));
                //display("Server is listening on port:"+tfsPort.getText());
                ta.append("Server is listening on port:"+tfsPort.getText() + "\n");
                ta.setCaretPosition(ta.getText().length() - 1);

                // infinite loop to wait for connections
                while(keepGoing)
                {
                    // format message saying we are waiting

                    Socket socket = serverSocket.accept();  	// accept connection

                    ObjectInputStream sInput=null;		// to write on the socket

                    /* Creating both Data Stream */
                    try
                    {
                        sInput = new ObjectInputStream(socket.getInputStream());
                    }
                    catch (IOException eIO) {
                        display("Exception creating new Input/output Streams: " + eIO);
                    }

                    try {
                        String msg = ((ChatMessage) sInput.readObject()).getMessage();
                        System.out.println("Msg:"+msg);
                        if(msg.startsWith("__KEY_EXCHANGE_1__")){ //bob
                            p = new BigInteger(msg.substring(msg.lastIndexOf("p:") + 2, msg.lastIndexOf("g:")));
                            g = new BigInteger(msg.substring(msg.lastIndexOf("g:") + 2, msg.lastIndexOf(";")));

                            a = new BigInteger(10, new Random()).abs();
                            A = g.modPow(a, p);
                            System.out.println("Bob p=" + p + ", g=" + g + ", a=" + a + ", A=" + A + ";");
                            send("__KEY_EXCHANGE_2__A:" + String.valueOf(A) + ";");
                        }
                        else if(msg.startsWith("__KEY_EXCHANGE_2__")){ //alice
                            A = new BigInteger(msg.substring(msg.lastIndexOf("A:") + 2, msg.lastIndexOf(";")));
                            b = new BigInteger(10, new Random()).abs();
                            B = g.modPow(b, p);
                            K = A.modPow(b, p);
                            byteKey = Arrays.copyOfRange(K.toByteArray(), 0, 16);
                            send("__KEY_EXCHANGE_3__B:" + String.valueOf(B) + ";");

                            System.out.println("Alice 2 p=" + p + ", g=" + g + ", A=" + A + ", b=" + b + ", B=" + B + ", K=" + K);
                            display("Connection succeeded");
                        }
                        else if(msg.startsWith("__KEY_EXCHANGE_3__")){//bob
                            B = new BigInteger(msg.substring(msg.lastIndexOf("B:") + 2, msg.lastIndexOf(";")));
                            K = B.modPow(a, p);
                            byteKey = Arrays.copyOfRange(K.toByteArray(), 0, 16);
                            System.out.println("Bob 3 p=" + p + ", g=" + g + ", A=" + A + ", a=" + a + ", B=" + B + ", K=" + K);
                            display("Connection succeeded");
                        }


                        else {
                            display(socket.getInetAddress()+": " + socket.getPort() + ": " +decrypt(msg, byteKey));
                        }
                        sInput.close();
                        socket.close();
                    } catch (IOException ex) {
                        display("Exception creating new Input/output Streams: " + ex);
                    } catch (ClassNotFoundException ex) {
                        Logger.getLogger(P2PClient.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
            }
            // something went bad
            catch (IOException e) {
//            String msg = sdf.format(new Date()) + " Exception on new ServerSocket: " + e + "\n";
//			display(msg);
            }
        }
    }
}
