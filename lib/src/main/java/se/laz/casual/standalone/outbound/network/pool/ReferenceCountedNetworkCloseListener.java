/*
 * Copyright (c) 2022, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.standalone.outbound.network.pool;

@FunctionalInterface
public interface ReferenceCountedNetworkCloseListener
{
    void closed(ReferenceCountedNetworkConnection networkConnection);
}
