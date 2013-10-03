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
package org.fusesource.fabric.api.jmx;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.MappingIterator;
import org.codehaus.jackson.map.ObjectMapper;
import org.fusesource.fabric.api.Container;
import org.fusesource.fabric.api.ContainerProvider;
import org.fusesource.fabric.api.CreateChildContainerOptions;
import org.fusesource.fabric.api.CreateContainerBasicOptions;
import org.fusesource.fabric.api.FabricRequirements;
import org.fusesource.fabric.api.FabricService;
import org.fusesource.fabric.api.MQService;
import org.fusesource.fabric.api.Profile;
import org.fusesource.fabric.api.ProfileRequirements;
import org.fusesource.fabric.api.Version;
import org.fusesource.fabric.internal.Objects;
import org.fusesource.fabric.service.MQServiceImpl;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An MBean for working with the global A-MQ topology configuration inside the Fabric profiles
 */
@Component(description = "Fabric MQ Manager JMX MBean")
public class MQManager implements MQManagerMXBean {
    private static final transient Logger LOG = LoggerFactory.getLogger(MQManager.class);

    public static final String DATA = "data";
    public static final String CONFIG_URL = "config";
    public static final String GROUP = "group";
    public static final String NETWORKS = "network";
    public static final String NETWORK_USER_NAME = "network.userName";
    public static final String NETWORK_PASSWORD = "network.password";
    public static final String PARENT = "parent";

    private static ObjectName OBJECT_NAME;

    static {
        try {
            OBJECT_NAME = new ObjectName("org.fusesource.fabric:type=MQManager");
        } catch (MalformedObjectNameException e) {
            // ignore
        }
    }

    @Reference(referenceInterface = FabricService.class)
    private FabricService fabricService;
    @Reference(referenceInterface = MBeanServer.class)
    private MBeanServer mbeanServer;

    private MQService mqService;

    @Activate
    void activate(ComponentContext context) throws Exception {
        Objects.notNull(fabricService, "fabricService");
        mqService = createMQService(fabricService);
        if (mbeanServer != null) {
            JMXUtils.registerMBean(this, mbeanServer, OBJECT_NAME);
        }
    }

    @Deactivate
    void deactivate() throws Exception {
        if (mbeanServer != null) {
            JMXUtils.unregisterMBean(mbeanServer, OBJECT_NAME);
        }
    }


    @Override
    public List<MQBrokerConfigDTO> loadBrokerConfiguration() {
        List<MQBrokerConfigDTO> answer = new ArrayList<MQBrokerConfigDTO>();
        Map<String, Profile> profileMap = getActiveOrRequiredBrokerProfileMap();
        Collection<Profile> values = profileMap.values();
        for (Profile profile : values) {
            MQBrokerConfigDTO dto = createConfigDTO(profile);
            if (dto != null) {
                answer.add(dto);
            }
        }
        return answer;
    }

    protected MQBrokerConfigDTO createConfigDTO(Profile profile) {
        MQBrokerConfigDTO dto = new MQBrokerConfigDTO();
        String brokerName = profile.getId();
        dto.setName(brokerName);
        String version = profile.getVersion();
        dto.setVersion(version);
        Profile[] parents = profile.getParents();
        if (parents != null && parents.length > 0) {
            dto.setParentProfile(parents[0].getId());
        }
        Map<String, String> configuration = mqService.getMQConfiguration(brokerName, profile);
        if (configuration != null) {
            dto.setConfigUrl(configuration.get(CONFIG_URL));
            dto.setData(configuration.get(DATA));
            dto.setGroup(configuration.get(GROUP));
            dto.setNetworks(configuration.get(NETWORKS));
            dto.setNetworksPassword(configuration.get(NETWORK_USER_NAME));
            dto.setNetworksPassword(configuration.get(NETWORK_PASSWORD));
            dto.setNetworks(configuration.get(NETWORKS));
            dto.setNetworks(configuration.get(NETWORKS));
        }
        return dto;
    }

    public Map<String, Profile> getActiveOrRequiredBrokerProfileMap() {
        return getActiveOrRequiredBrokerProfileMap(fabricService.getDefaultVersion());
    }

    public Map<String, Profile> getActiveOrRequiredBrokerProfileMap(Version version) {
        Objects.notNull(fabricService, "fabricService");
        Map<String, Profile> profileMap = new HashMap<String, Profile>();
        if (version != null) {
            FabricRequirements requirements = fabricService.getRequirements();
            Profile[] profiles = version.getProfiles();
            for (Profile profile : profiles) {
                Map<String, Map<String, String>> configurations = profile.getConfigurations();
                Set<Map.Entry<String, Map<String, String>>> entries = configurations.entrySet();
                for (Map.Entry<String, Map<String, String>> entry : entries) {
                    String key = entry.getKey();
                    if (key.startsWith(MQService.MQ_FABRIC_SERVER_PID_PREFIX)) {
                        String brokerName = key.substring(MQService.MQ_FABRIC_SERVER_PID_PREFIX.length());
                        String profileId = profile.getId();

                        // ignore if we don't have any requirements or instances as it could be profiles such
                        // as the out of the box mq-default / mq-amq etc
                        if (requirements.hasMinimumInstances(profileId) || profile.getAssociatedContainers().length > 0) {
                            System.out.println("Broker name: " + brokerName + " profile " + profileId);
                            profileMap.put(brokerName, profile);
                        }
                    }
                }
            }
        }
        return profileMap;
    }

    @Override
    public void saveBrokerConfigurationJSON(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationConfig.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        List<MQBrokerConfigDTO> dtos = new ArrayList<MQBrokerConfigDTO>();
        MappingIterator<Object> iter = mapper.reader(MQBrokerConfigDTO.class).readValues(json);
        while (iter.hasNext()) {
            Object next = iter.next();
            if (next instanceof MQBrokerConfigDTO) {
                dtos.add((MQBrokerConfigDTO) next);
            } else {
                LOG.warn("Expected MQBrokerConfigDTO but parsed invalid DTO " + next);
            }
        }
        saveBrokerConfiguration(dtos);
    }

    public void saveBrokerConfiguration(List<MQBrokerConfigDTO> dtos) throws IOException {
        for (MQBrokerConfigDTO dto : dtos) {
            createOrUpdateProfile(dto, fabricService);
        }
    }


    /**
     * Creates or updates the broker profile for the given DTO and updates the requirements so that the
     * minimum number of instances of the profile is updated
     */
    public static Profile createOrUpdateProfile(MQBrokerConfigDTO dto, FabricService fabricService) throws IOException {
        FabricRequirements requirements = fabricService.getRequirements();
        MQService mqService = createMQService(fabricService);
        HashMap<String, String> configuration = new HashMap<String, String>();

        List<String> properties = dto.getProperties();
        String version = dto.version();

        if (properties != null) {
            for (String entry : properties) {
                String[] parts = entry.split("=", 2);
                if (parts.length == 2) {
                    configuration.put(parts[0], parts[1]);
                } else {
                    configuration.put(parts[0], "");
                }
            }
        }

        String data = dto.getData();
        String name = dto.getName();
        if (data == null) {
            //data = System.getProperty("karaf.base") + System.getProperty("file.separator") + "data" + System.getProperty("file.separator") + name;
            // lets use a cross-container friendly location for the data
            data = "${karaf.base}/data/" + name;
        }
        configuration.put(DATA, data);

        String config = dto.getConfigUrl();
        if (config != null) {
            configuration.put(CONFIG_URL, mqService.getConfig(version, config));
        }

        String group = dto.getGroup();
        if (group != null) {
            configuration.put(GROUP, group);
        }

        String networks = dto.getNetworks();
        if (networks != null) {
            configuration.put(NETWORKS, networks);
        }

        String networksUserName = dto.getNetworksUserName();
        if (networksUserName != null) {
            configuration.put(NETWORK_USER_NAME, networksUserName);
        }

        String networksPassword = dto.getNetworksPassword();
        if (networksPassword != null) {
            configuration.put(NETWORK_PASSWORD, networksPassword);
        }

        String parentProfile = dto.getParentProfile();
        if (parentProfile != null) {
            configuration.put(PARENT, parentProfile);
        }

        Profile profile = mqService.createMQProfile(version, name, configuration);
        String profileId = profile.getId();
        ProfileRequirements profileRequirement = requirements.getOrCreateProfileRequirement(profileId);
        Integer minimumInstances = profileRequirement.getMinimumInstances();
        if (minimumInstances == null || minimumInstances.intValue() < dto.requiredInstances()) {
            profileRequirement.setMinimumInstances(dto.requiredInstances());
            fabricService.setRequirements(requirements);
        }
        return profile;
    }

    protected static MQServiceImpl createMQService(FabricService fabricService) {
        return new MQServiceImpl(fabricService);
    }

    public static void assignProfileToContainers(FabricService fabricService, Profile profile, String[] assignContainers) {
        for (String containerName : assignContainers) {
            try {
                Container container = fabricService.getContainer(containerName);
                if (container == null) {
                    System.out.println("Failed to assign profile to " + containerName + ": profile doesn't exists");
                } else {
                    HashSet<Profile> profiles = new HashSet<Profile>(Arrays.asList(container.getProfiles()));
                    profiles.add(profile);
                    container.setProfiles(profiles.toArray(new Profile[profiles.size()]));
                    System.out.println("Profile successfully assigned to " + containerName);
                }
            } catch (Exception e) {
                System.out.println("Failed to assign profile to " + containerName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Creates container builders for the given DTO
     */
    public static List<CreateContainerBasicOptions.Builder> createContainerBuilders(MQBrokerConfigDTO dto,
                                                                                    FabricService fabricService, String containerProviderScheme,
                                                                                    String profileId, String version,
                                                                                    String[] createContainers) throws IOException {

        ContainerProvider containerProvider = fabricService.getProvider(containerProviderScheme);
        Objects.notNull(containerProvider, "No ContainerProvider available for scheme: " + containerProviderScheme);

        List<CreateContainerBasicOptions.Builder> containerBuilders = new ArrayList<CreateContainerBasicOptions.Builder>();
        for (String container : createContainers) {

            String type = null;
            String parent = fabricService.getCurrentContainerName();

            String jmxUser = dto.getUsername();
            String jmxPassword = dto.getPassword();
            String jvmOpts = dto.getJvmOpts();

            CreateContainerBasicOptions.Builder builder = containerProvider.newBuilder();

            builder = (CreateContainerBasicOptions.Builder) builder
                    .name(container)
                    .parent(parent)
                    .number(dto.requiredInstances())
                    .ensembleServer(false)
                    .proxyUri(fabricService.getMavenRepoURI())
                    .jvmOpts(jvmOpts)
                    .zookeeperUrl(fabricService.getZookeeperUrl())
                    .zookeeperPassword(fabricService.getZookeeperPassword())
                    .profiles(profileId)
                    .version(version);

            if (builder instanceof CreateChildContainerOptions.Builder) {
                CreateChildContainerOptions.Builder childBuilder = (CreateChildContainerOptions.Builder) builder;
                builder = childBuilder.jmxUser(jmxUser).jmxPassword(jmxPassword);
            }
            containerBuilders.add(builder);
        }
        return containerBuilders;
    }
}
