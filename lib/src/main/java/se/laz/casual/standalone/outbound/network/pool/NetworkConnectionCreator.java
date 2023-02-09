/*
 * Copyright (c) 2022, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.standalone.outbound.network.pool;

import se.laz.casual.network.ProtocolVersion;
import se.laz.casual.network.outbound.NetworkListener;
import se.laz.casual.standalone.outbound.Address;

@FunctionalInterface
public interface NetworkConnectionCreator
{
    ReferenceCountedNetworkConnection createNetworkConnection(Address address, ProtocolVersion protocolVersion, NetworkListener networkListener, ReferenceCountedNetworkCloseListener referenceCountedNetworkCloseListener, NetworkListener ownListener);
}
