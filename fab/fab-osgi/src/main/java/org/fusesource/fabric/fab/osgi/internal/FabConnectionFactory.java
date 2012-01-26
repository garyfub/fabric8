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

package org.fusesource.fabric.fab.osgi.internal;

import org.fusesource.fabric.fab.osgi.ServiceConstants;
import org.ops4j.pax.url.commons.handler.ConnectionFactory;
import org.ops4j.util.property.PropertyResolver;
import org.osgi.framework.BundleContext;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/**
 * {@link ConnectionFactory} for the "fab" protocol
 */
public class FabConnectionFactory implements ConnectionFactory<Configuration> {

    public URLConnection createConection(BundleContext bundleContext, URL url, Configuration config) throws MalformedURLException {
        String protocol = url.getProtocol();
        if (ServiceConstants.PROTOCOL_FAB.equals(protocol)) {
            return new FabConnection(url, config, bundleContext);
        }
        throw new MalformedURLException("Unsupported protocol: " + protocol);
    }

    public Configuration createConfiguration(PropertyResolver propertyResolver) {
        return new Configuration(propertyResolver);
    }
}
