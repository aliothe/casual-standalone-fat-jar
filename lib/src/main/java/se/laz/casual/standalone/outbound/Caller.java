/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.standalone.outbound;

import se.laz.casual.api.CasualQueueApi;
import se.laz.casual.api.CasualServiceApi;

import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;

public interface Caller extends CasualServiceApi, CasualQueueApi
{
    void close();
    boolean isDisconnected();
    XAResource getXAResource();
}
