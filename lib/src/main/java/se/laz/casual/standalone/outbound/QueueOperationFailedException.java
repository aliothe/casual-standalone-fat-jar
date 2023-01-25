/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.standalone.outbound;

public class QueueOperationFailedException extends RuntimeException
{
    private static final long serialVersionUID = 1L;
    public QueueOperationFailedException(String s)
    {
        super(s);
    }
}
