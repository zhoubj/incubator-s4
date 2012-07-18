package org.apache.s4.core;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.configuration.ConfigurationConverter;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.s4.base.Hasher;
import org.apache.s4.base.SerializerDeserializer;
import org.apache.s4.base.util.S4RLoaderFactory;
import org.apache.s4.comm.DefaultHasher;
import org.apache.s4.comm.serialize.KryoSerDeser;
import org.apache.s4.core.ft.CheckpointingFramework;
import org.apache.s4.core.ft.NoOpCheckpointingFramework;
import org.apache.s4.deploy.DeploymentManager;
import org.apache.s4.deploy.DistributedDeploymentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.name.Names;

/**
 * Temporary module allowing assignment from ZK, communication through Netty, and distributed deployment management,
 * until we have a better way to customize node configuration
 * 
 */
public class DefaultCoreModule extends AbstractModule {

    private static Logger logger = LoggerFactory.getLogger(DefaultCoreModule.class);

    InputStream coreConfigFileInputStream;
    private PropertiesConfiguration config;

    String clusterName = null;

    public DefaultCoreModule(InputStream coreConfigFileInputStream) {
        this.coreConfigFileInputStream = coreConfigFileInputStream;
    }

    @Override
    protected void configure() {
        if (config == null) {
            loadProperties(binder());
        }
        if (coreConfigFileInputStream != null) {
            try {
                coreConfigFileInputStream.close();
            } catch (IOException ignored) {
            }
        }

        /* The hashing function to map keys top partitions. */
        bind(Hasher.class).to(DefaultHasher.class);

        /* Use Kryo to serialize events. */
        bind(SerializerDeserializer.class).to(KryoSerDeser.class);

        bind(DeploymentManager.class).to(DistributedDeploymentManager.class);

        bind(S4RLoaderFactory.class);

        // For enabling checkpointing, one needs to use a custom module, such as
        // org.apache.s4.core.ft.FileSytemBasedCheckpointingModule
        bind(CheckpointingFramework.class).to(NoOpCheckpointingFramework.class);
    }

    private void loadProperties(Binder binder) {
        try {
            config = new PropertiesConfiguration();
            config.load(coreConfigFileInputStream);

            // TODO - validate properties.

            /* Make all properties injectable. Do we need this? */
            Names.bindProperties(binder, ConfigurationConverter.getProperties(config));

        } catch (ConfigurationException e) {
            binder.addError(e);
            e.printStackTrace();
        }
    }

}
