/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.standalone.outbound;

import se.laz.casual.network.outbound.NetworkListener;

import javax.transaction.TransactionManager;

public interface CallerProducer
{
    Caller createCaller(TransactionManager transactionManager, String host, int port, NetworkListener networkListener);
}
