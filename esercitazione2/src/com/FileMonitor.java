package com;

import java.util.*;

public class FileMonitor {

    private Set<String> fileAperti;
    //Non ho bisogno del lock: ho una singola coda uso il wait-notify.

    public FileMonitor(){
        //HashSet (O(1)) è più perfomante di TreeSet (O(log n)).
        this.fileAperti = new HashSet<>();
    }

    /**
     * Tenta di aprire un file nel monitor.
     *
     * @param filename file da aprire
     * @return vero se aperto con successo.
     */
    public synchronized boolean openFile(String filename){
        if(!fileAperti.contains(filename)){
            fileAperti.add(filename);
            return true;
        }

        return false;
    }

    /**
     * Tenta di chiudere un file nel monitor.
     *
     * @param filename file da chiudere
     * @return vero se il file è stato chiuso.
     */
    public synchronized boolean closeFile(String filename){
        if(fileAperti.contains(filename)){
           fileAperti.remove(filename);
           return true;
        }

        return false;
    }

}
