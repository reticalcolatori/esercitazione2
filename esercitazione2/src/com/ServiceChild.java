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

//Risassuntino per la presentazione:
//Finché non arriva EOF, il cliente mi invierà dei nomi file:
//1) Controllo sull'esistenza del file
//2) Lettura della dimensione.
//3) Scrivo il file che mi passa il cliente.
//4) Invio ACK
//Alla fine quando arriva EOF:
//1) Chiudo prima input(non ricevo più nulla)
//2) Chiudo l'output.

public class ServiceChild extends Thread {

    // Risposte da inviare al client
    private static final String RESULT_ATTIVA = "attiva";
    private static final String RESULT_SALTA_FILE = "salta file";
    private static final String RESULT_OK = "OK";

    // Codici errore
    private static final int SOCK_ERR = 1;
    private static final int NET_ERR = 2;

    private Socket client = null;
    private FileMonitor monitor;

    public ServiceChild(Socket client, FileMonitor monitor) {
        this.client = client;
        this.monitor = monitor;
    }

    //PROTOCOLLO
    // 1) ricevo il nome del file --> verifico se esiste nella directory corrente e rispondo o RESULT_ATTIVA o RESULT_SALTA_FILE
    // 2) leggo la dimensione del file
    // 3) leggo gli n byte del file
    // 4) se tutto ok invia OK

    @Override
    public void run() {
        DataInputStream inSocket = null;
        DataOutputStream outSocket = null;

        String nomeCurrFile = "";

        try {
            inSocket = new DataInputStream(client.getInputStream());
            outSocket = new DataOutputStream(client.getOutputStream());
        } catch (IOException e) {
            System.err.println("Impossibile estrarre canali di comunicazione dalla socket!");
            //Non esco perchè ho una grossa perdita.
            //Meglio che fallisca il thread che l'intero processo.
            //System.exit(SOCK_ERR);
        }

        try {
            while ((nomeCurrFile = inSocket.readUTF()) != null) {
                //Tento di aprire il file nel monitor.
                if (monitor.openFile(nomeCurrFile)) {
                    if (!Files.exists(Path.of(new File(nomeCurrFile).toURI()))) { //se il file non esiste il server richiede il trasf.
                        outSocket.writeUTF(RESULT_ATTIVA);

                        //da questo in poi il protocollo procede inviando dimensione prima e file dopo
                        long dim = -1;
                        dim = inSocket.readLong();

                        //Creo il file nel fs nella directory corrente
                        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(nomeCurrFile))) {
                            for (int i = 0; i < dim; i++) {
                                bufferedWriter.write(inSocket.read());
                            }
                        } catch (IOException e) {
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

                    //Chiudo il file aperto nel monitor.
                    monitor.closeFile(nomeCurrFile);
                } else { //altrimenti il file è gia presente nel fs --> salta file
                    outSocket.writeUTF(RESULT_SALTA_FILE);
                }
            }
        } catch (IOException e) {
            System.err.println("Errore nella lettura del nome del file!");
            e.printStackTrace();
            //Non esco perchè ho una grossa perdita.
            //Meglio che fallisca il thread che l'intero processo.
            //System.exit(NET_ERR);
        }

        try {
            client.shutdownInput();         //Posso chiudere l'input non ricevo più nulla.
            outSocket.writeUTF(RESULT_OK);  //Invio una conferma chiusura.
            client.shutdownOutput();        //Chiudo l'output.
            client.close();                 //Rilascio risorse.
        } catch (IOException e) {
            e.printStackTrace();
            //Non esco perchè ho una grossa perdita.
            //Meglio che fallisca il thread che l'intero processo.
            //System.exit(NET_ERR);
        }

        //Se faccio la exit mi esce tutto il processo.
        //Basta lasciare il metodo così termina da solo il thread.
        //System.exit(0);
    }

}