package es.bsc.conn.docker;

import es.bsc.conn.Connector;
import es.bsc.conn.clients.docker.DockerClient;
import es.bsc.conn.clients.exceptions.ConnClientException;
import es.bsc.conn.exceptions.ConnException;
import es.bsc.conn.loggers.Loggers;
import es.bsc.conn.types.HardwareDescription;
import es.bsc.conn.types.SoftwareDescription;
import es.bsc.conn.types.VirtualResource;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig.Builder;


public class Docker extends Connector {

    // Properties' names
    private static final String PROP_PROVIDER_USER = "provider-user";
    private static final String PROP_VM_USER = "vm-user";

    // Open ssh daemon and wait for Master commands
    private static final String[] WORKER_CMD = { "/usr/sbin/sshd", "-D" };

    // Ports properties
    private static final String PROP_HTTPS = "https";
    private static final String PROP_HTTP = "http";
    private static final String PROP_TCP = "tcp";
    private static final int NUM_NIO_PORTS = 1_100;
    private static final int NIO_BASE_PORT = 43_000;
    private static final int SSH_PORT = 22;

    // This is the user of all the compss:compss base images
    private static final String IMAGE_USERNAME = "compss";

    // Logger
    private static final Logger LOGGER = LogManager.getLogger(Loggers.DOCKER);

    // Docker Client
    private DockerClient dockerClient;

    // Information about requests
    private final Map<String, HardwareDescription> containeridToHardwareRequest = new HashMap<>();
    private final Map<String, SoftwareDescription> containeridToSoftwareRequest = new HashMap<>();


    public Docker(Map<String, String> props) throws ConnException {
        super(props);

        // Get properties -----------------------------
        LOGGER.info("Initializing Docker Connector");
        // The host would ideally be the swarm manager. This way, we create containers there,
        // and it schedules them wherever it needs to
        // Must be of the form tcp://1.2.3.4:5678
        // But the Server prop always substitutes tcp by http, so we undo the change
        String host = server.replace(PROP_HTTPS, PROP_TCP).replace(PROP_HTTP, PROP_TCP);

        // Build DockerClient with the specified properties
        Builder b = DefaultDockerClientConfig.createDefaultConfigBuilder();
        b = b.withDockerHost(host); // Host

        DockerClientConfig config = b.build();
        dockerClient = DockerClient.build(config);
    }

    @Override
    public Object create(HardwareDescription hd, SoftwareDescription sd, Map<String, String> prop) throws ConnException {
        try {
            int[] exposedPorts = new int[NUM_NIO_PORTS + 1];
            for (int i = 0; i < NUM_NIO_PORTS; ++i) {
                exposedPorts[i] = NIO_BASE_PORT + i;
            }
            exposedPorts[NUM_NIO_PORTS] = SSH_PORT; // SSH

            String containerId = dockerClient.createContainer(hd.getImageName(), appName, exposedPorts, hd.getTotalComputingUnits(),
                    hd.getMemorySize(), WORKER_CMD);

            dockerClient.startContainer(containerId);

            containeridToHardwareRequest.put(containerId, hd);
            containeridToSoftwareRequest.put(containerId, sd);

            return containerId;
        } catch (Exception e) {
            String err = "There was an error creating the container '" + appName + "': " + e.getMessage();
            throw new ConnException(err);
        }
    }

    @Override
    public VirtualResource waitUntilCreation(Object id) throws ConnException {
        // We don't have to wait, since DockerClient is synchronous. By the time
        // this is called, the container resource's been created and started (if no errors, ofc).

        String containerId = (String) id;
        Container c = dockerClient.getContainerById(containerId);
        if (c == null) {
            String err = "The container " + containerId + " couldn't be retrieved.";
            throw new ConnException(err);
        }

        // Retrieve information
        String ip = dockerClient.getIpAddress(containerId);

        // Create Virtual Resource
        VirtualResource vr = new VirtualResource();
        vr.setId(containerId);
        vr.setIp(ip);
        HashMap<String, String> providerProperties = new HashMap<String, String>();
        providerProperties.put(PROP_PROVIDER_USER, IMAGE_USERNAME);
        providerProperties.put(PROP_VM_USER, IMAGE_USERNAME);
        vr.setProperties(providerProperties);

        HardwareDescription hd = containeridToHardwareRequest.get(containerId);
        if (hd == null) {
            throw new ConnException("Unregistered hardware description for containerId = " + containerId);
        }
        try {
            getHardwareInformation(containerId, hd);
        } catch (ConnClientException cce) {
            throw new ConnException("Error retrieving resource hardware description of Container " + containerId + " from client", cce);
        }
        vr.setHd(hd);

        SoftwareDescription sd = containeridToSoftwareRequest.get(containerId);
        if (sd == null) {
            throw new ConnException("Unregistered software description for containerId = " + containerId);
        }
        sd.setOperatingSystemType("Linux");
        vr.setSd(sd);

        return vr;
    }

    @Override
    public void destroy(Object id) {
        String containerId = (String) id;
        Container c = dockerClient.getContainerById(containerId);
        dockerClient.removeContainer(c.getId());

        containeridToHardwareRequest.remove(containerId);
        containeridToSoftwareRequest.remove(containerId);
    }

    @Override
    public float getPriceSlot(VirtualResource virtualResource) {
        return virtualResource.getHd().getPricePerUnit();
    }

    @Override
    public void close() {
        dockerClient.removeAllContainers();
    }

    private void getHardwareInformation(String containerId, HardwareDescription hd) throws ConnClientException {
        // In the Docker old version this code was:

        // Remove COMPSs from the packages list.
        // In Docker images, COMPSs is already installed in the worker.
        // There's no need to transfer/install COMPSs.
        /*
         * for (ApplicationPackage ap : imageDescription.getPackages()) { if (ap.getSource().endsWith("COMPSs.tar.gz"))
         * { imageDescription.getPackages().remove(ap); } }
         * 
         * imageDescription.getConfig().setUser(IMAGE_USERNAME); imageDescription.getConfig().setAppDir(IMAGE_APP_DIR);
         */
    }

}