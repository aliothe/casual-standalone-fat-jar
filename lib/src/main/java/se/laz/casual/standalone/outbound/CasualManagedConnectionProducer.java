/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.standalone.outbound;

import javax.transaction.TransactionManager;
import java.util.Objects;
import java.util.function.Supplier;

public class CasualManagedConnectionProducer
{
    private CasualManagedConnectionProducer()
    {}

    public static CasualManagedConnection create(Supplier<TransactionManager> transactionManagerSupplier, String host, int port)
    {
        Objects.requireNonNull(transactionManagerSupplier, "transactionManagerSupplier can not be null");
        Objects.requireNonNull(host, "host can not be null");
        return CasualManagedConnectionImpl.of(transactionManagerSupplier.get(), host, port);
    }
}
