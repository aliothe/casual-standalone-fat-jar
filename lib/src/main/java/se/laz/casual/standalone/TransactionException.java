/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.standalone;

public class TransactionException extends RuntimeException
{
    private static final long serialVersionUID = 1L;
    public TransactionException()
    {
        super();
    }

    public TransactionException(String message)
    {
        super(message);
    }

    public TransactionException(Throwable t)
    {
        super(t);
    }

    public TransactionException(String message, Throwable t)
    {
        super(message, t);
    }
}
