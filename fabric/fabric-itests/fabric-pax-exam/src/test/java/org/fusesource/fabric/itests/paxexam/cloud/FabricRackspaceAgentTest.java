/**
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource.fabric.itests.paxexam.cloud;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.annotation.Nullable;
import com.google.common.base.Predicate;
import org.apache.commons.io.IOUtils;
import org.fusesource.fabric.itests.paxexam.FabricCommandsTestSupport;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeState;
import org.jclouds.ec2.EC2Client;
import org.jclouds.ec2.domain.IpProtocol;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openengsb.labs.paxexam.karaf.options.LogLevelOption;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.ExamReactorStrategy;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.exam.spi.reactors.AllConfinedStagedReactorFactory;


import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.openengsb.labs.paxexam.karaf.options.KarafDistributionOption.debugConfiguration;
import static org.openengsb.labs.paxexam.karaf.options.KarafDistributionOption.editConfigurationFileExtend;
import static org.openengsb.labs.paxexam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.openengsb.labs.paxexam.karaf.options.KarafDistributionOption.logLevel;
import static org.ops4j.pax.exam.CoreOptions.scanFeatures;


@RunWith(JUnit4TestRunner.class)
@ExamReactorStrategy(AllConfinedStagedReactorFactory.class)
public class FabricRackspaceAgentTest extends FabricCommandsTestSupport {

    private String identity;
    private String credential;
    private String image;
    private String location;
    private String user;
    private String group = "fabricitests";

    /**
     * Returns true if all the requirements for running this test are meet.
     * @return
     */
    public boolean isReady() {
        return
                identity != null && credential != null && image != null & user != null &&
                        !identity.isEmpty() && !credential.isEmpty() && !image.isEmpty() && !user.isEmpty();
    }

    @Before
    public void setUp() {
        identity = System.getProperty("fabricitest.rackspace.identity");
        credential = System.getProperty("fabricitest.rackspace.credential");
        image = System.getProperty("fabricitest.rackspace.image");
        location = System.getProperty("fabricitest.rackspace.location");
        user = System.getProperty("fabricitest.rackspace.user");
    }


    @After
    public void tearDown() {
        if (isReady()) {
            System.err.println(executeCommand("group-destroy " + group, 30000L, false));
        }
    }

    /**
     * Starts an ensemble server on EC2, configures the security groups and join the ensemble.
     *
     * @throws InterruptedException
     * @throws java.io.IOException
     */
    @Test
    public void testRackspaceAgentCreation() throws InterruptedException, IOException {
        if (!isReady()) {
            System.err.println("Rackspace is not setup correctly. This test will not run.");
            System.err.println("To prpoerly run this test, you need to setup with maven the following properties:");
            System.err.println("fabricitest.rackspace.identity \t The rackspace access id");
            System.err.println("fabricitest.rackspace.credential \t The rackspace access key");
            System.err.println("fabricitest.rackspace.image  \t The rackspace (java ready) image");
            System.err.println("fabricitest.rackspace.user  \t The user under which the agent will run");
            return;
        }

        System.err.println(executeCommand("features:install jclouds-cloudserver-us fabric-jclouds jclouds-commands"));

        //Filtering out regions because there is a temporary connectivity issue with us-west-2.
        executeCommands("config:edit org.jclouds.compute-rackspace",
                "config:propset provider cloudservers-us ",
                "config:propset identity " + identity,
                "config:propset credential " + credential,
                "config:update");

        ComputeService computeService = getOsgiService(ComputeService.class, 3*DEFAULT_TIMEOUT);

        //The compute service needs some time to properly initialize.
        Thread.sleep(3 * DEFAULT_TIMEOUT);
        System.err.println(executeCommand(String.format("fabric:agent-create --ensemble-server --url jclouds://cloudservers-us?imageId=%s&locationId=%s&group=%s&user=%s --profile default ensemble1", image, location, group, user), 10 * 60000L, false));
        String publicIp = getNodePublicIp(computeService);
        assertNotNull(publicIp);
        System.err.println(executeCommand("fabric:join " + publicIp + ":2181", 10 * 60000L, false));
        Thread.sleep(DEFAULT_TIMEOUT);
        System.err.println(executeCommand("fabric:join " + publicIp + ":2181", 10 * 60000L, false));
        String agentList = executeCommand("fabric:agent-list");
        System.err.println(agentList);
        assertTrue(agentList.contains("root") && agentList.contains("ensemble1"));

    }

    /**
     * Return the public ip of the generated node.
     * It assumes that no other node is currently running using the current group.
     * @return
     */
    private String getNodePublicIp(ComputeService computeService) {

        for (ComputeMetadata computeMetadata : computeService.listNodesDetailsMatching(new Predicate<ComputeMetadata>() {
            @Override
            public boolean apply(@Nullable ComputeMetadata metadata) {
                NodeMetadata nodeMetadata = (NodeMetadata) metadata;
                return nodeMetadata.getGroup().equals(group) && nodeMetadata.getState().equals(NodeState.RUNNING);
            }
        })) {
            NodeMetadata nodeMetadata = (NodeMetadata) computeMetadata;
            return nodeMetadata.getPublicAddresses().toArray(new String[0])[0];

        }
        return null;
    }

    /**
     * @return the IP address of the client on which this code is running.
     * @throws java.io.IOException
     */
    protected String getOriginatingIp() throws IOException {
        URL url = new URL("http://checkip.amazonaws.com/");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.connect();
        return IOUtils.toString(connection.getInputStream()).trim() + "/32";
    }

    @Configuration
    public Option[] config() {
        return new Option[]{
                fabricDistributionConfiguration(), keepRuntimeFolder(), logLevel(LogLevelOption.LogLevel.ERROR),
                editConfigurationFileExtend("etc/system.properties", "fabricitest.rackspace.identity", System.getProperty("fabricitest.rackspace.identity") != null ? System.getProperty("fabricitest.rackspace.identity") : ""),
                editConfigurationFileExtend("etc/system.properties", "fabricitest.rackspace.credential", System.getProperty("fabricitest.rackspace.credential") != null ? System.getProperty("fabricitest.rackspace.credential") : ""),
                editConfigurationFileExtend("etc/system.properties", "fabricitest.rackspace.image", System.getProperty("fabricitest.rackspace.image") != null ? System.getProperty("fabricitest.rackspace.image") : ""),
                editConfigurationFileExtend("etc/system.properties", "fabricitest.rackspace.location", System.getProperty("fabricitest.rackspace.location") != null ? System.getProperty("fabricitest.rackspace.location") : ""),
                editConfigurationFileExtend("etc/system.properties", "fabricitest.rackspace.user", System.getProperty("fabricitest.rackspace.user") != null ? System.getProperty("fabricitest.rackspace.user") : ""),
                editConfigurationFileExtend("etc/config.properties", "org.osgi.framework.executionenvironment", "JavaSE-1.7,JavaSE-1.6,JavaSE-1.5"),
                scanFeatures("jclouds","jclouds-compute").start()
        };
    }


}
