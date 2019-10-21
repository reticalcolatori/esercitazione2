package com;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;


//Risassuntino per la presentazione:
//1) Controllo argomenti
//2) Istanzio oggetto client
//3) REPL in cui chiedo all'utente il direttorio da copiare.
//   Durante il REPL viene gestita una connessione TCP.

public class MultiplePutClient {

    private static final int SOCKET_CONN_ERR = 1; // Connessione non andata a buon fine.
    private static final int FILE_NOT_EXISTS_ERR = 2; // File non trovato
    private static final int IO_ERR = 3; // Errore IO
    private static final int ARGS_ERR = 4; // Errore negli argomenti

    // Invocazione client:
    // MultiplePutClient serverAddress serverPort dimSoglia

	// Il client trasferisce solo se il file supera una dimensione minima.
	// Protocollo richiesta put file:
	// 1) invio nomefile

	// 2) In ricezione ho una conferma: se positiva posso trasferire il file.
	// L'esito è positivo solo se nel direttorio corrente del server non è presente
	// un file con nome uguale.
	// Il server rimane sullo stesso direttorio.

	// 3)Invio la lunghezza
	// lunghezza(long)

	// 4)Il server invia un ACK

	// 5)Quando ho finito di inviare tutti i file (anche 0) faccio la shutdown di
	// output.
	// 6)Il server invierà una conferma.

    // Risposte dal server
    private static final String RESULT_ATTIVA = "attiva";
    private static final String RESULT_SALTA_FILE = "salta file";
    private static final String RESULT_OK = "OK";

    private static boolean isPortValid(int port) {
        return 1024 < port && port < 0x10000;
    }

    public static void main(String[] args) {
        // MultiplePutClient serverAddress serverPort dimSoglia

        if (args.length != 4) {
            System.err.println("usage java MultiplePutClient serverAddress serverPort dimSoglia");
            System.exit(ARGS_ERR);
        }

        int dimensioneSoglia = 0;

        InetAddress serverAddress = null;
        int serverPort = 0;

        try {
            serverAddress = InetAddress.getByName(args[0]);
            serverPort = Integer.parseInt(args[1]);
            dimensioneSoglia = Integer.parseInt(args[3]);

        } catch (IllegalArgumentException | IOException e) {
            e.printStackTrace();
            System.exit(ARGS_ERR);
        }

        //Checks
        if (!isPortValid(serverPort)){
            System.err.println(("server port non valida"));
            System.exit(ARGS_ERR);
        }

        if (dimensioneSoglia < 0){
            System.err.println(("dimensioneSoglia non valida (<0)"));
            System.exit(ARGS_ERR);
        }

        //Posso avviare il client
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String dirname = "";
        try {
            while ((dirname = in.readLine()) != null) {
                // Apro la directory e e recupero un array dei files
                File dirFile = new File(dirname);
                Path dirPath = Paths.get(dirFile.toURI());

                // Controllo directory.
                if (!dirFile.isDirectory()) {
                    System.out.println("Directory non valida");
                    return;
                }

                // Estraggo i file contenuti nella directory
                File[] files = dirFile.listFiles();

                // Directory vuota non devo fare nulla.
                if (files.length == 0) {
                    System.out.println("Directory vuota, non eseguo trasferimenti.");
                    return;
                }

                //preparo strutture input/output
                Socket socket = null;
                BufferedInputStream socketIn = null;
                BufferedOutputStream socketOut = null;

                DataInputStream socketDataIn = null;
                DataOutputStream socketDataOut = null;

                String risposta = null;

                // Apro la connessione
                try {
                    socket = new Socket(serverAddress, serverPort);
                    socketIn = new BufferedInputStream(socket.getInputStream());
                    socketOut = new BufferedOutputStream(socket.getOutputStream());

                    socketDataIn = new DataInputStream(socketIn);
                    socketDataOut = new DataOutputStream(socketOut);
                } catch (IOException e) {
                    // se ho IOExeption devo terminare
                    e.printStackTrace();
                    System.exit(SOCKET_CONN_ERR);
                }

                for (File file : files) {
                    // Per ogni file verifico se la dimensione supera la soglia minima
                    long dimFile = file.length();

                    if (dimFile > dimensioneSoglia) {
                        // Posso inviare la richesta
                        try {
                            socketDataOut.writeUTF(file.getName());
                        } catch (IOException e) {
                            // Non riesco a inviare la richiesta continuo alla prossima.
                            e.printStackTrace();
                            continue;
                        }

                        // Ricevo risposta
                        try {
                            risposta = socketDataIn.readUTF();
                        } catch (IOException e) {
                            // Errore lettura salto
                            e.printStackTrace();
                            continue;
                        }

                        // Decodifico la risposta
                        if (RESULT_ATTIVA.equalsIgnoreCase(risposta)) {

                            // Invio lunghezza file
                            try {
                                socketDataOut.writeLong(dimFile);
                            } catch (IOException e) {
                                // Perché esco?
                                // Il server, non ricevendo la lunghezza del file, andrà in attesa fino a timeout
                                e.printStackTrace();
                                System.exit(IO_ERR);
                            }

                            // Posso inviare il file.
                            try (InputStreamReader inFile = new FileReader(file)) {
                                int tmpByte;
                                while ((tmpByte = inFile.read()) >= 0)
                                    socketOut.write(tmpByte);

                            } catch (IOException ex) {
                                // Perché esco?
                                // Il server, non ricevendo tutti i byte del file, andrà in attesa fino a timeout
                                ex.printStackTrace();
                                System.exit(IO_ERR);
                            }

                            // Ricevo ACK da server.ì
                            try {
                                risposta = socketDataIn.readUTF();
                            } catch (IOException e) {
                                // Errore lettura salto
                                e.printStackTrace();
                                continue;
                            }

                            // La nostra non è solo una scelta (furba tra l'altro), ma è proprio
                            // una specifica: "Il Multiple put viene effettuato file
                            // per file con assenso del server per ogni file" !!
                            if (RESULT_OK.equalsIgnoreCase(risposta)) {
                                System.out.println("File " + file.getName() + " caricato.");
                            } else {
                                System.out.println("Errore nell'invio " + file.getName() + ": " + risposta);
                            }

                        }
                    }
                }

                // Ho finito di inviare i file: chiudo la connessione.
                try {
                    socket.shutdownOutput();                        //Non invio più nulla.
                    System.out.println(socketDataIn.readUTF());        //Attendo una conferma chiusura.
                    socket.shutdownInput();                            //Chiudo l'input.
                    socket.close();                                    //Rilascio risorse.
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(IO_ERR);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(IO_ERR);
        }

        //chiudo il canale di lettura da stdin
        try {
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
