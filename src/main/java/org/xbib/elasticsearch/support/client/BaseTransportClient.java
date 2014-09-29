package org.xbib.elasticsearch.support.client;

import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.elasticsearch.common.collect.Sets.newHashSet;

public abstract class BaseTransportClient {

    private final static ESLogger logger = ESLoggerFactory.getLogger(BaseTransportClient.class.getSimpleName());

    private final Set<InetSocketTransportAddress> addresses = newHashSet();

    protected TransportClient client;

    protected ConfigHelper configHelper = new ConfigHelper();

    protected void createClient(Map<String,String> settings) {
        createClient(ImmutableSettings.settingsBuilder().put(settings).build());
    }

    protected void createClient(Settings settings) {
        if (client != null) {
            logger.warn("client is open, closing...");
            client.close();
            client.threadPool().shutdown();
            logger.warn("client is closed");
            client = null;
        }
        if (settings != null) {
            logger.info("creating transport client, java version {}, effective settings {}",
                    System.getProperty("java.version"), settings.getAsMap());
            // false = do not load config settings from environment
            this.client = new TransportClient(settings, false);
        } else {
            logger.info("creating transport client, java version {}, using default settings",
                    System.getProperty("java.version"));
            this.client = new TransportClient();
        }
        try {
            connect(settings);
        } catch (UnknownHostException e) {
            logger.error(e.getMessage(), e);
        } catch (SocketException e) {
            logger.error(e.getMessage(), e);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public Client client() {
        return client;
    }

    public List<String> getConnectedNodes() {
        return ClientHelper.getConnectedNodes(client);
    }

    public synchronized void shutdown() {
        if (client != null) {
            logger.debug("shutdown started");
            client.close();
            client.threadPool().shutdown();
            client = null;
            logger.debug("shutdown complete");
        }
        addresses.clear();
    }

    protected Settings findSettings() {
        ImmutableSettings.Builder settingsBuilder = ImmutableSettings.settingsBuilder();
        settingsBuilder.put("host", "localhost");
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            logger.debug("the hostname is {}", hostname);
            settingsBuilder.put("scheme", "es")
                    .put("host", hostname)
                    .put("port", 9300);
        } catch (UnknownHostException e) {
            logger.warn("can't resolve host name, probably something wrong with network config: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }
        return settingsBuilder.build();
    }

    protected void connect(Settings settings) throws IOException {
        String hostname = settings.get("host");
        int port = settings.getAsInt("port", 9300);
        boolean newaddresses = false;
        switch (hostname) {
            case "hostname": {
                InetSocketTransportAddress address = new InetSocketTransportAddress(InetAddress.getLocalHost().getHostName(), port);
                if (!addresses.contains(address)) {
                    logger.info("adding hostname address for transport client: {}", address);
                    client.addTransportAddress(address);
                    addresses.add(address);
                    newaddresses = true;
                }
                break;
            }
            case "interfaces": {
                Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
                for (NetworkInterface netint : Collections.list(nets)) {
                    logger.info("checking network interface = {}", netint.getName());
                    Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                    for (InetAddress addr : Collections.list(inetAddresses)) {
                        logger.info("checking address = {}", addr.getHostAddress());
                        InetSocketTransportAddress address = new InetSocketTransportAddress(addr, port);
                        if (!addresses.contains(address)) {
                            logger.info("adding address to transport client: {}", address);
                            client.addTransportAddress(address);
                            addresses.add(address);
                            newaddresses = true;
                        }
                    }
                }
                break;
            }
            case "inet4": {
                Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
                for (NetworkInterface netint : Collections.list(nets)) {
                    logger.info("checking network interface = {}", netint.getName());
                    Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                    for (InetAddress addr : Collections.list(inetAddresses)) {
                        if (addr instanceof Inet4Address) {
                            logger.info("checking address = {}", addr.getHostAddress());
                            InetSocketTransportAddress address = new InetSocketTransportAddress(addr, port);
                            if (!addresses.contains(address)) {
                                logger.info("adding address for transport client: {}", address);
                                client.addTransportAddress(address);
                                addresses.add(address);
                                newaddresses = true;
                            }
                        }
                    }
                }
                break;
            }
            case "inet6": {
                Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
                for (NetworkInterface netint : Collections.list(nets)) {
                    logger.info("checking network interface = {}", netint.getName());
                    Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                    for (InetAddress addr : Collections.list(inetAddresses)) {
                        if (addr instanceof Inet6Address) {
                            logger.info("checking address = {}", addr.getHostAddress());
                            InetSocketTransportAddress address = new InetSocketTransportAddress(addr, port);
                            if (!addresses.contains(address)) {
                                logger.info("adding address for transport client: {}", address);
                                client.addTransportAddress(address);
                                addresses.add(address);
                                newaddresses = true;
                            }
                        }
                    }
                }
                break;
            }
            default: {
                InetSocketTransportAddress address = new InetSocketTransportAddress(hostname, port);
                if (!addresses.contains(address)) {
                    logger.info("adding custom address for transport client: {}", address);
                    client.addTransportAddress(address);
                    addresses.add(address);
                    newaddresses = true;
                }
                break;
            }
        }
        logger.info("configured addresses to connect: {}", addresses);
        if (client.connectedNodes() != null) {
            List<DiscoveryNode> nodes = client.connectedNodes().asList();
            logger.info("connected nodes = {}", nodes);
            if (newaddresses) {
                for (DiscoveryNode node : nodes) {
                    logger.info("new connection to {}", node);
                }
                if (!nodes.isEmpty()) {
                    if (settings.get("es.sniff") != null) {
                        try {
                            connectMore();
                        } catch (Exception e) {
                            logger.error("error while connecting to more nodes", e);
                        }
                    }
                }
            }
        }
    }

    public ImmutableSettings.Builder getSettingsBuilder() {
        return configHelper.settingsBuilder();
    }

    public void resetSettings() {
        configHelper.reset();
    }

    public void setting(InputStream in) throws IOException {
        configHelper.setting(in);
    }

    public void addSetting(String key, String value) {
        configHelper.setting(key, value);
    }

    public void addSetting(String key, Boolean value) {
        configHelper.setting(key, value);
    }

    public void addSetting(String key, Integer value) {
        configHelper.setting(key, value);
    }

    public void setSettings(Settings settings) {
        configHelper.settings(settings);
    }

    public Settings getSettings() {
        return configHelper.settings();
    }

    public void mapping(String type, String mapping) throws IOException {
        configHelper.mapping(type, mapping);
    }

    public void mapping(String type, InputStream in) throws IOException {
        configHelper.mapping(type, in);
    }

    public Map<String, String> getMappings() {
        return configHelper.mappings();
    }

    private void connectMore() throws IOException {
        logger.info("trying to discover more nodes...");
        ClusterStateResponse clusterStateResponse = client.admin().cluster().state(new ClusterStateRequest()).actionGet();
        DiscoveryNodes nodes = clusterStateResponse.getState().getNodes();
        for (DiscoveryNode node : nodes) {
            logger.info("adding discovered node {}", node);
            try {
                client.addTransportAddress(node.address());
            } catch (Exception e) {
                logger.warn("can't add node " + node, e);
            }
        }
        logger.info("... discovery done");
    }

}
