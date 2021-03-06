/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.s4.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.helix.HelixManager;
import org.apache.helix.HelixManagerFactory;
import org.apache.helix.InstanceType;
import org.apache.helix.controller.HelixControllerMain;
import org.apache.s4.comm.DefaultCommModule;
import org.apache.s4.comm.HelixBasedCommModule;
import org.apache.s4.comm.topology.ClusterNode;
import org.apache.s4.core.util.ParametersInjectionModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.io.Resources;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.google.inject.util.Modules.OverriddenModuleBuilder;

/**
 * Bootstrap class for S4. It creates an S4 node.
 * 
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Starts an S4 server.
     * 
     * @param args
     */
    public static void main(String[] args) {

        MainArgs mainArgs = new MainArgs();
        JCommander jc = new JCommander(mainArgs);

        try {
            jc.parse(args);
        } catch (Exception e) {
            JCommander.getConsole().println("Cannot parse arguments: " + e.getMessage());
            jc.usage();
            System.exit(1);
        }

        startNode(mainArgs);
    }

    private static void startNode(MainArgs mainArgs) {
        try {
            Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {

                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    logger.error("Uncaught exception in thread {}", t.getName(), e);

                }
            });
            Injector injector;
            InputStream commConfigFileInputStream;
            InputStream coreConfigFileInputStream;
            String commConfigString;
            if (mainArgs.commConfigFilePath == null) {
                commConfigFileInputStream = Resources.getResource("default.s4.comm.properties").openStream();
                commConfigString = "default.s4.comm.properties from classpath";
            } else {
                commConfigFileInputStream = new FileInputStream(new File(mainArgs.commConfigFilePath));
                commConfigString = mainArgs.commConfigFilePath;
            }

            String coreConfigString;
            if (mainArgs.coreConfigFilePath == null) {
                coreConfigFileInputStream = Resources.getResource("default.s4.core.properties").openStream();
                coreConfigString = "default.s4.core.properties from classpath";
            } else {
                coreConfigFileInputStream = new FileInputStream(new File(mainArgs.coreConfigFilePath));
                coreConfigString = mainArgs.coreConfigFilePath;
            }

            logger.info(
                    "Initializing S4 node with : \n- comm module class [{}]\n- comm configuration file [{}]\n- core module class [{}]\n- core configuration file[{}]\n- extra modules: {}\n- inline parameters: {}",
                    new String[] { mainArgs.commModuleClass, commConfigString, mainArgs.coreModuleClass,
                            coreConfigString, Arrays.toString(mainArgs.extraModulesClasses.toArray(new String[] {})),
                            Arrays.toString(mainArgs.extraNamedParameters.toArray(new String[] {})) });

            AbstractModule commModule = (AbstractModule) Class.forName(mainArgs.commModuleClass)
                    .getConstructor(InputStream.class, String.class)
                    .newInstance(commConfigFileInputStream, mainArgs.clusterName);
            AbstractModule coreModule = (AbstractModule) Class.forName(mainArgs.coreModuleClass)
                    .getConstructor(InputStream.class).newInstance(coreConfigFileInputStream);

            List<com.google.inject.Module> extraModules = new ArrayList<com.google.inject.Module>();
            for (String moduleClass : mainArgs.extraModulesClasses) {
                extraModules.add((Module) Class.forName(moduleClass).newInstance());
            }
            Module combinedModule = Modules.combine(commModule, coreModule);
            if (extraModules.size() > 0) {
                OverriddenModuleBuilder overridenModuleBuilder = Modules.override(combinedModule);
                combinedModule = overridenModuleBuilder.with(extraModules);
            }

            if (mainArgs.zkConnectionString != null) {
                mainArgs.extraNamedParameters.add("s4.cluster.zk_address=" + mainArgs.zkConnectionString);
            }
            if (mainArgs.instanceName != null) {
                mainArgs.extraNamedParameters.add("s4.instance.name=" + mainArgs.instanceName);
            }

            if (!mainArgs.extraNamedParameters.isEmpty()) {
                logger.debug("Adding named parameters for injection : {}",
                        Arrays.toString(mainArgs.extraNamedParameters.toArray(new String[] {})));
                Map<String, String> namedParameters = new HashMap<String, String>();

                for (String namedParam : mainArgs.extraNamedParameters) {
                    namedParameters.put(namedParam.split("[=]")[0].trim(),
                            namedParam.substring(namedParam.indexOf('=') + 1).trim());
                }
                combinedModule = Modules.override(combinedModule).with(new ParametersInjectionModule(namedParameters));
            }
            
            injector = Guice.createInjector(combinedModule);
            //start a HelixController to manage the cluster
            String controllerName = Inet4Address.getLocalHost().getCanonicalHostName() + UUID.randomUUID().toString();
            HelixControllerMain.startHelixController(mainArgs.zkConnectionString, mainArgs.clusterName, controllerName, HelixControllerMain.STANDALONE);

            if (mainArgs.appClass != null) {
                logger.info("Starting S4 node with single application from class [{}]", mainArgs.appClass);
                App app = (App) injector.getInstance(Class.forName(mainArgs.appClass));
                app.init();
                app.start();
            } else {
                logger.info("Starting S4 node. This node will automatically download applications published for the cluster it belongs to");
                Server server = injector.getInstance(Server.class);
                try {
                    server.start(injector);
                } catch (Exception e) {
                    logger.error("Failed to start the controller.", e);
                }
            }
        } catch (Exception e) {
            logger.error("Cannot start S4 node", e);
            System.exit(1);
        }
    }

    /**
     * Defines command parameters.
     * 
     */
    @Parameters(separators = "=")
    public static class MainArgs {

        @Parameter(names = { "-c", "-cluster" }, description = "cluster name", required = true)
        String clusterName = null;
        
        @Parameter(names = { "-id", "-nodeId" }, description = "Node/Instance id that uniquely identifies a node", required = false)
        String instanceName = null;

        @Parameter(names = "-commModuleClass", description = "configuration module class for the communication layer", required = false)
        String commModuleClass = HelixBasedCommModule.class.getName();

        @Parameter(names = "-commConfig", description = "s4 communication layer configuration file", required = false)
        String commConfigFilePath;

        @Parameter(names = "-coreModuleClass", description = "s4-core configuration module class", required = false)
        String coreModuleClass = DefaultCoreModule.class.getName();

        @Parameter(names = "-coreConfig", description = "s4 core configuration file", required = false)
        String coreConfigFilePath = null;

        @Parameter(names = "-appClass", description = "App class to load. This will disable dynamic downloading but allows to start apps directly. These app classes must have been loaded first, usually through a custom module.", required = false, hidden = true)
        String appClass = null;

        @Parameter(names = { "-extraModulesClasses", "-emc" }, description = "Comma-separated list of additional configuration modules (they will be instantiated through their constructor without arguments).", required = false, hidden = false)
        List<String> extraModulesClasses = new ArrayList<String>();

        @Parameter(names = { "-namedStringParameters", "-p" }, description = "Comma-separated list of inline configuration parameters, taking precedence over homonymous configuration parameters from configuration files. Syntax: '-p=name1=value1,name2=value2 '", hidden = false, converter = InlineConfigParameterConverter.class)
        List<String> extraNamedParameters = new ArrayList<String>();

        @Parameter(names = "-zk", description = "Zookeeper connection string", required = false)
        String zkConnectionString;

    }

    /**
     * Parameters parsing utility.
     * 
     */
    public static class InlineConfigParameterConverter implements IStringConverter<String> {

        @Override
        public String convert(String arg) {
            Pattern parameterPattern = Pattern.compile("(\\S+=\\S+)");
            logger.info("processing inline configuration parameter {}", arg);
            Matcher parameterMatcher = parameterPattern.matcher(arg);
            if (!parameterMatcher.find()) {
                throw new IllegalArgumentException("Cannot understand parameter " + arg);
            }
            return parameterMatcher.group(1);
        }
    }

}
