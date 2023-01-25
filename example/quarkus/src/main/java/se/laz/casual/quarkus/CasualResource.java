/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.quarkus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.IOUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.ServiceReturn;
import se.laz.casual.api.buffer.type.OctetBuffer;
import se.laz.casual.api.flags.AtmiFlags;
import se.laz.casual.api.flags.Flag;
import se.laz.casual.api.flags.ServiceReturnState;
import se.laz.casual.quarkus.db.Fruit;
import se.laz.casual.standalone.outbound.Caller;
import se.laz.casual.standalone.outbound.CasualManagedConnection;
import se.laz.casual.standalone.outbound.CasualManagedConnectionProducer;
import se.laz.casual.standalone.outbound.ServiceCallFailedException;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

@Path("/casual")
public class CasualResource
{
    private static final Logger LOG = Logger.getLogger(CasualResource.class.getName());
    private CasualManagedConnection managedConnection;
    TransactionManager transactionManager;
    EntityManager entityManager;

    @Inject
    public CasualResource(
             TransactionManager transactionManager,
             EntityManager entityManager,
             @ConfigProperty(name = "casual.host") String host,
             @ConfigProperty(name = "casual.port") String port)
    {
        this.transactionManager = transactionManager;
        this.entityManager = entityManager;
        managedConnection = CasualManagedConnectionProducer.create(() -> transactionManager, host, Integer.parseInt(port));
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String ping()
    {
        return "Hello world";
    }

    @Transactional
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Path("{serviceName}")
    public Response serviceRequest(@PathParam("serviceName") String serviceName,
                                   InputStream inputStream,
                                   @DefaultValue("true") @QueryParam("trans") boolean trans)
    {
        try
        {
            byte[] data = IOUtils.toByteArray(inputStream);
            Flag<AtmiFlags> flags = trans ? Flag.of(AtmiFlags.NOFLAG) : Flag.of(AtmiFlags.TPNOTRAN);
            OctetBuffer buffer = OctetBuffer.of(data);
            return Response.ok().entity(makeCasualCall(buffer, serviceName, flags).getBytes().get(0)).build();
        }
        catch (Exception e)
        {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            return Response.serverError().entity(sw.toString()).build();
        }
    }

    @Path("fruit")
    @GET
    public List<Fruit> get()
    {
        return entityManager.createNamedQuery("Fruits.findAll", Fruit.class).getResultList();
    }

    @POST
    @Path("fruit")
    @Transactional
    public Response create(Fruit fruit)
    {
        if (fruit.getId() != null)
        {
            throw new WebApplicationException("Id was invalidly set on request.", 422);
        }
        entityManager.persist(fruit);
        return Response.ok(fruit).status(201).build();
    }

    @GET
    @Path("fruit/{id}")
    public Fruit getSingleFruit(Integer id)
    {
        Fruit entity = entityManager.find(Fruit.class, id);
        if (entity == null)
        {
            throw new WebApplicationException("Fruit with id of " + id + " does not exist.", 404);
        }
        return entity;
    }

    @Transactional
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("fruitrollback")
    public Response rollback(Fruit fruit)
    {
        try
        {
            LOG.info(() -> "start:\nthread id: " + Thread.currentThread().getId());
            if (fruit.getId() != null)
            {
                throw new WebApplicationException("Id was invalidly set on request.", 422);
            }
            entityManager.persist(fruit);
            Fruit entity = entityManager.find(Fruit.class, fruit.getId());
            if (entity == null)
            {
                throw new WebApplicationException("Fruit with id of " + fruit.getId() + " was not stored.", 404);
            }
            Flag<AtmiFlags> flags = Flag.of(AtmiFlags.NOFLAG);
            OctetBuffer buffer = OctetBuffer.of(new String("rolling").getBytes(StandardCharsets.UTF_8));
            makeCasualCall(buffer, "casual/example/rollback", flags);
            return Response.ok("insert and rollback ok, entry: " + fruit + " should not have been persisted").build();
        }
        catch (Exception e)
        {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            return Response.serverError().entity(sw.toString()).build();
        }
    }

    @PreDestroy
    public void goingAway()
    {
        LOG.warning(() -> "Bean going away, closing caller");
        managedConnection.close();
    }

    private CasualBuffer makeCasualCall(CasualBuffer msg, String serviceName, Flag<AtmiFlags> flags)
    {
        Caller caller = managedConnection.getCaller().orElseThrow(() -> new RuntimeException("currently no caller, either never connected or disconnected and not yet reconnected"));
        ServiceReturn<CasualBuffer> reply = caller.tpcall(serviceName, msg, flags);
        if(reply.getServiceReturnState() == ServiceReturnState.TPSUCCESS)
        {
            return reply.getReplyBuffer();
        }
        throw new ServiceCallFailedException("tpcall failed: " + reply.getErrorState());
    }

    @Provider
    public static class ErrorMapper implements ExceptionMapper<Exception>
    {
        @Inject
        ObjectMapper objectMapper;

        @Override
        public Response toResponse(Exception exception)
        {
            LOG.warning(() -> "Failed to handle request: " + exception);

            int code = 500;
            if (exception instanceof WebApplicationException)
            {
                code = ((WebApplicationException) exception).getResponse().getStatus();
            }

            ObjectNode exceptionJson = objectMapper.createObjectNode();
            exceptionJson.put("exceptionType", exception.getClass().getName());
            exceptionJson.put("code", code);

            if (exception.getMessage() != null)
            {
                exceptionJson.put("error", exception.getMessage());
            }

            return Response.status(code)
                           .entity(exceptionJson)
                           .build();
        }

    }

}