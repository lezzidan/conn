package es.bsc.conn.dummy;

/*
Test app for Dummy connector
 */
import es.bsc.conn.types.HardwareDescription;
import es.bsc.conn.types.SoftwareDescription;

import java.util.HashMap;

public class test {
    public static void main(String[] args) {
        System.out.println("Hello Testing API code!"); // Display the string.

        Dummy d = new Dummy(new HashMap<String, String>());
        HardwareDescription hd = new HardwareDescription();
        SoftwareDescription sd = new SoftwareDescription();
        HashMap<String, String> prop = new HashMap<String, String>();
        Object id = d.create(hd, sd, prop);
        //Integer id = (Integer) d.create(hd, sd, prop);
        System.out.println("VM id: "+id);
        d.close();

    }}
