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
package org.fusesource.mq.fabric;

import org.apache.activemq.transport.discovery.DiscoveryAgent;
import org.apache.activemq.transport.discovery.DiscoveryAgentFactory;
import org.apache.activemq.util.IOExceptionSupport;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static org.apache.activemq.util.URISupport.*;

public class FabricDiscoveryAgentFactory extends DiscoveryAgentFactory {

    protected DiscoveryAgent doCreateDiscoveryAgent(URI uri) throws IOException {
        try {
            
            FabricDiscoveryAgent rc = new FabricDiscoveryAgent();
            if( uri.getSchemeSpecificPart()!=null && uri.getSchemeSpecificPart().length() > 0 ){
                String ssp = stripPrefix(uri.getSchemeSpecificPart(), "//");
                Map<String, String> query = parseQuery(ssp);
                String groupName = ssp.split("\\?")[0];
                if( query.get("id")!=null ) {
                    rc.setId(query.get("id"));
                }
                rc.setGroupName(groupName);
            }
            return rc;
            
        } catch (Throwable e) {
            throw IOExceptionSupport.create("Could not create discovery agent: " + uri, e);
        }
    }
}
