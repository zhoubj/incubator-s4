package org.apache.s4.comm;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import org.apache.commons.configuration.ConfigurationConverter;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.helix.participant.statemachine.StateModelFactory;
import org.apache.s4.base.Emitter;
import org.apache.s4.base.Hasher;
import org.apache.s4.base.Listener;
import org.apache.s4.base.RemoteEmitter;
import org.apache.s4.base.SerializerDeserializer;
import org.apache.s4.comm.helix.S4StateModelFactory;
import org.apache.s4.comm.serialize.KryoSerDeser;
import org.apache.s4.comm.tcp.RemoteEmitters;
import org.apache.s4.comm.topology.Assignment;
import org.apache.s4.comm.topology.AssignmentFromHelix;
import org.apache.s4.comm.topology.AssignmentFromZK;
import org.apache.s4.comm.topology.Cluster;
import org.apache.s4.comm.topology.ClusterFromHelix;
import org.apache.s4.comm.topology.ClusterFromZK;
import org.apache.s4.comm.topology.Clusters;
import org.apache.s4.comm.topology.ClustersFromHelix;
import org.apache.s4.comm.topology.ClustersFromZK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Names;

public class HelixBasedCommModule extends AbstractModule{

    private static Logger logger = LoggerFactory.getLogger(DefaultCommModule.class);
    InputStream commConfigInputStream;
    private PropertiesConfiguration config;
    String clusterName;

    /**
     * 
     * @param commConfigInputStream
     *            input stream from a configuration file
     * @param clusterName
     *            the name of the cluster to which the current node belongs. If specified in the configuration file,
     *            this parameter will be ignored.
     */
    public HelixBasedCommModule(InputStream commConfigInputStream, String clusterName) {
        super();
        this.commConfigInputStream = commConfigInputStream;
        this.clusterName = clusterName;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void configure() {
        if (config == null) {
            loadProperties(binder());
        }
        if (commConfigInputStream != null) {
            try {
                commConfigInputStream.close();
            } catch (IOException ignored) {
            }
        }

        /* The hashing function to map keys top partitions. */
        bind(Hasher.class).to(DefaultHasher.class);
        /* Use Kryo to serialize events. */
        bind(SerializerDeserializer.class).to(KryoSerDeser.class);

        // a node holds a single partition assignment
        // ==> Assignment and Cluster are singletons so they can be shared between comm layer and app.
        bind(StateModelFactory.class).annotatedWith(Names.named("s4.task.statemodelfactory")).to(S4StateModelFactory.class);
        bind(Assignment.class).to(AssignmentFromHelix.class);
        bind(Cluster.class).to(ClusterFromHelix.class);

        bind(Clusters.class).to(ClustersFromHelix.class);

        try {
            Class<? extends Emitter> emitterClass = (Class<? extends Emitter>) Class.forName(config
                    .getString("s4.comm.emitter.class"));
            bind(Emitter.class).to(emitterClass);

            // RemoteEmitter instances are created through a factory, depending on the topology. We inject the factory
            Class<? extends RemoteEmitter> remoteEmitterClass = (Class<? extends RemoteEmitter>) Class.forName(config
                    .getString("s4.comm.emitter.remote.class"));
            install(new FactoryModuleBuilder().implement(RemoteEmitter.class, remoteEmitterClass).build(
                    RemoteEmitterFactory.class));
            bind(RemoteEmitters.class);

            bind(Listener.class).to(
                    (Class<? extends Listener>) Class.forName(config.getString("s4.comm.listener.class")));

        } catch (ClassNotFoundException e) {
            logger.error("Cannot find class implementation ", e);
        }

    }

    @SuppressWarnings("serial")
    private void loadProperties(Binder binder) {
        try {
            config = new PropertiesConfiguration();
            config.load(commConfigInputStream);

            // TODO - validate properties.

            /* Make all properties injectable. Do we need this? */
            Names.bindProperties(binder, ConfigurationConverter.getProperties(config));

            if (clusterName != null) {
                if (config.containsKey("s4.cluster.name")) {
                    logger.warn(
                            "cluster [{}] passed as a parameter will not be used because an existing cluster.name parameter of value [{}] was found in the configuration file and will be used",
                            clusterName, config.getProperty("s4.cluster.name"));
                } else {
                    Names.bindProperties(binder, new HashMap<String, String>() {
                        {
                            put("s4.cluster.name", clusterName);
                        }
                    });
                }
            }

        } catch (ConfigurationException e) {
            binder.addError(e);
            e.printStackTrace();
        }
    }

}
