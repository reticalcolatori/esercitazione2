package com;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

public class ServiceChild extends Thread {

    // Risposte da inviare al client
	private static final String RESULT_ATTIVA = "attiva";
	private static final String RESULT_SALTA_FILE = "salta file";
    private static final String RESULT_OK = "OK";
    
    // Codici errore
    private static final int SOCK_ERR = 1;
    private static final int NET_ERR = 2; 

    private Socket client = null;

    public ServiceChild(Socket client){
        this.client = client;
    }

    //PROTOCOLLO
    // 1) ricevo il nome del file --> verifico se esiste nella directory corrente e rispondo o RESULT_ATTIVA o RESULT_SALTA_FILE
    // 2) leggo la dimensione del file
    // 3) leggo gli n byte del file
    // 4) se tutto ok invia OK

    @Override
    public void run(){
        DataInputStream inSocket = null;
        DataOutputStream outSocket = null;

        String nomeCurrFile = "";
        
        try {
            inSocket = new DataInputStream(client.getInputStream());
            outSocket = new DataOutputStream(client.getOutputStream());
        } catch (IOException e) {
            System.err.println("Impossibile estrarre canali di comunicazione dalla socket!");
            System.exit(SOCK_ERR);
        }

        try {
            while ((nomeCurrFile = inSocket.readUTF()) != null) {
                if (!Files.exists(Path.of(new File(nomeCurrFile).toURI()))) { //se il file non esiste il server richiede il trasf.
                    outSocket.writeUTF(RESULT_ATTIVA);

                    //da questo in poi il protocollo procede inviando dimensione prima e file dopo
                    long dim = -1;
                    dim = inSocket.readLong();

                    //Creo il file nel fs nella directory corrente
                    try(BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(nomeCurrFile))){
                        for (int i = 0; i < dim; i++) {
                            bufferedWriter.write(inSocket.read());
                        }
                    } catch(IOException e) {
                        String err = "Errore nel creare il file: " + e.getMessage();
                        System.err.println(err);
                        //oltre a dire questo comunico anche al mio cliente che la trasmissione non è andata a buon fine
                        outSocket.writeUTF("FAILED");
                        continue;
                    }

                    //la comunicazione è andata a buon fine! comunico al cliente!
                    outSocket.writeUTF(RESULT_OK);
                
                } else { //altrimenti il file è gia presente nel fs --> salta file
                    outSocket.writeUTF(RESULT_SALTA_FILE);
                }
            }
        } catch (IOException e) {
            System.err.println("Errore nella lettura del nome del file!");
            e.printStackTrace();
            System.exit(NET_ERR);
        }

        client.close();
        System.exit(0);
    }

}