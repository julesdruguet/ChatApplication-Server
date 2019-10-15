/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chatapplication_server.components.ClientSocketEngine;

import chatapplication_server.ComponentManager;

import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.cert.CertificateException;

import static chatapplication_server.components.ServerSocketEngine.SocketConnectionHandler.decrypt;

/**
 * @author atgianne
 */
public class ListenFromServer extends Thread {
    public void run() {
        while (true) {
            ObjectInputStream sInput = ClientEngine.getInstance().getStreamReader();

            synchronized (sInput) {
                try {
                    String encryptedMsg = (String) sInput.readObject();
                    String msg = null;

                    SecretKeySpec symmetricKey;
                    if ((symmetricKey = ClientEngine.getInstance().getSymmetricKey()) != null) {
                        msg = decrypt(encryptedMsg, symmetricKey.getEncoded());
                        if (msg.contains("#")) {
                            ClientSocketGUI.getInstance().appendPrivateChat(msg + "\n");
                        } else {
                            ClientSocketGUI.getInstance().append(msg + "\n");
                        }
                    }

                } catch (IOException e) {
                    ClientSocketGUI.getInstance().append("Server has closed the connection: " + e.getMessage() + "\n");
                    ComponentManager.getInstance().fatalException(e);
                } catch (ClassNotFoundException cfe) {
                    ClientSocketGUI.getInstance().append("Server has closed the connection: " + cfe.getMessage());
                    ComponentManager.getInstance().fatalException(cfe);
                }
            }
        }
    }
}
