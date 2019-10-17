package com;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class MainServer {

    private static final int NET_ERR = 1;

    public static final int PORT = 4444;

    /*
    STRUTTURA:
    1) Apro la mia ServerSocket
    2) E ciclicamente mi pongo in attesa di richieste di connesione
    3) Delego la socket creata per la specifica connessione ad un mio figlio
     */

    //MainServer [porta]

    public static void main(String[] args) throws IOException {
        int port = -1;

        if(args.length == 1){
            port = Integer.parseInt(args[0]);
        } else if(args.length == 0) {
            port = PORT;
        } else {
            throw new IOException();
        }

        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
        } catch (IOException e) {
            System.exit(NET_ERR);
        }

        try {
            while(true){
                Socket client = null;
                client = serverSocket.accept();
                new ServiceChild(client).start();
            }
        } catch (IOException e) {
            System.out.println("Problemi durante la connessione con il client");
            System.exit(NET_ERR);
        }

        
    }

}