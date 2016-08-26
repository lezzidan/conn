package es.bsc.conn.rocci;
import es.bsc.conn.Connector;
import es.bsc.conn.exceptions.ConnectorException;
import es.bsc.conn.types.HardwareDescription;
import es.bsc.conn.types.SoftwareDescription;
import es.bsc.conn.types.VirtualResource;

// TODO: add logger

import java.util.ArrayList;
import java.util.HashMap;

public class ROCCI extends Connector {
    private static final String ROCCI_CLIENT_VERSION = "4.2.5";

    private static final Integer RETRY_TIME = 5; 				// Seconds
    private static final long DEFAULT_TIME_SLOT = 5;		// Minutes

    private RocciClient client;
    private ArrayList<String> cmd_string;
    private String attributes = "";

    private static Integer MAX_VM_CREATION_TIME = 10; 	// Minutes
    private static Integer MAX_ALLOWED_ERRORS = 3;		// Number of maximum errors
    private long timeSlot = DEFAULT_TIME_SLOT;

    public ROCCI(HashMap<String, String> props){
        super(props);
        cmd_string = new ArrayList<String>();

        // ROCCI client parameters setup
        if (props.get("Server") != null) {
            cmd_string.add("--endpoint " + props.get("Server"));
        }

        if (props.get("auth") != null) {
            cmd_string.add("--auth " + props.get("auth"));
        }

        if (props.get("timeout") != null) {
            cmd_string.add("--timeout " + props.get("timeout"));
        }

        if (props.get("username") != null) {
            cmd_string.add("--username " + props.get("username"));
        }

        if (props.get("password") != null) {
            cmd_string.add("--password " + props.get("password"));
        }

        if (props.get("ca-path") != null) {
            cmd_string.add("--ca-path " + props.get("ca-path"));
        }

        if (props.get("ca-file") != null) {
            cmd_string.add("--ca-file " + props.get("ca-file"));
        }

        if (props.get("skip-ca-check") != null) {
            cmd_string.add("--skip-ca-check " + props.get("skip-ca-check"));
        }

        if (props.get("filter") != null) {
            cmd_string.add("--filter " + props.get("filter"));
        }

        if (props.get("user-cred") != null) {
            cmd_string.add("--user-cred " + props.get("user-cred"));
        }

        if (props.get("voms") != null) {
            cmd_string.add("--voms");
        }

        if (props.get("media-type") != null) {
            cmd_string.add("--media-type " + props.get("media-type"));
        }

        if (props.get("resource") != null)
            cmd_string.add("--resource "+props.get("resource"));

        if (props.get("attributes") != null)
            cmd_string.add("--attributes "+props.get("attributes"));

        if (props.get("context") != null) {
            cmd_string.add("--context " + props.get("context"));
        }

        if (props.get("action") != null)
            cmd_string.add("--action "+props.get("action"));

        if (props.get("mixin") != null)
            cmd_string.add("--mixin "+props.get("mixin"));

        if (props.get("link") != null) {
            cmd_string.add("--link " + props.get("link"));
        }

        if (props.get("trigger-action") != null) {
            cmd_string.add("--trigger-action " + props.get("trigger-action"));
        }

        if (props.get("log-to") != null) {
            cmd_string.add("--log-to " + props.get("log-to"));
        }

        cmd_string.add("--output-format json_extended_pretty");

        if (props.get("dump-model") != null) {
            cmd_string.add("--dump-model");
        }

        if (props.get("debug") != null) {
            cmd_string.add("--debug");
        }

        if (props.get("verbose") != null) {
            cmd_string.add("--verbose");
        }

        // ROCCI connector parameters setup
        if (props.get("max-vm-creation-time") != null) {
            MAX_VM_CREATION_TIME = Integer.parseInt(props.get("max-vm-creation-time"));
        }

        if (props.get("max-connection-errors") != null) {
            MAX_ALLOWED_ERRORS = Integer.parseInt(props.get("max-connection-errors"));
        }

        if (props.get("owner") != null && props.get("jobname") != null) {
            attributes = props.get("owner") + "-" + props.get("jobname");
        }

        String time = props.get("time-slot");
        if (time != null) {
            timeSlot = Integer.parseInt(time)*1000;
        }else{
            timeSlot = DEFAULT_TIME_SLOT;
        }

        client = new RocciClient(cmd_string, attributes);
    }

    @Override
    public Object create(HardwareDescription hd, SoftwareDescription sd, HashMap<String, String> prop) throws ConnectorException {
        try {
            String instanceCode = sd.getImageType();
            String vmId = client.create_compute(sd.getImageName(), instanceCode);
            VirtualResource vr = new VirtualResource(vmId, hd, sd, prop);
            /*if (debug) {
                logger.debug("VM "+vmId+ " Created");
            }*/
            return vr;
        } catch (Exception e){
            //logger.error("Error creating a VM", e);
            System.out.println("Error creating a VM");
            throw new ConnectorException(e);
        }

    }

    @Override
    public VirtualResource waitUntilCreation(VirtualResource vr) throws ConnectorException {
        String vmId = vr.getId().toString();
        //logger.info("Waiting until VM "+ vmId +" is created");
        System.out.println("Waiting until VM "+ vmId +" is created");
        Integer polls = 0;
        int errors = 0;

        String status = null;
        status = client.get_resource_status(vmId);

        try {
            Thread.sleep(RETRY_TIME * 1000);
        } catch (InterruptedException e1) {
            //logger.warn("Sleep Interrumped", e1);
        }

        while (!status.equals("active")) {
            try {
                polls++;
                Thread.sleep(RETRY_TIME * 1000);
                if (RETRY_TIME * polls >= MAX_VM_CREATION_TIME * 60) {
                    //logger.error("Maximum VM waiting for creation time reached.");
                    throw new ConnectorException("Maximum VM creation time reached.");
                }
                status = client.get_resource_status(vmId);
                errors = 0;
            } catch (Exception e) {
                errors++;
                if (errors == MAX_ALLOWED_ERRORS) {
                    //logger.error("ERROR_MSG = [\n\tError = " + e.getMessage() + "\n]");
                    System.out.println("ERROR_MSG = [\n\tError = " + e.getMessage() + "\n]");
                    throw new ConnectorException("Error getting the status of the request");
                }
            }
        }
        String ip = client.get_resource_address(vmId);
        vr.setIp(ip);
        return vr;
    }

    @Override
    public void destroy(Object id) {
        String vmId = (String) id;
        //logger.info(" Destroy VM "+vmId+" with rOCCI connector");
        System.out.println(" Destroy VM "+vmId+" with rOCCI connector");
        client.delete_compute(vmId);
    }

    @Override
    public long getTimeSlot() {
        return timeSlot;
    }

    @Override
    public float getPriceSlot(VirtualResource virtualResource) {
        return virtualResource.getHd().getPricePerUnit();
    }

    @Override
    public void close() {
        //Nothing to do
    }

}
