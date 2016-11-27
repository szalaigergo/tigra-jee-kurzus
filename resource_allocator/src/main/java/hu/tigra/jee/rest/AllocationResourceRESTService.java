/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package hu.tigra.jee.rest;

import hu.tigra.jee.controller.AllocationController;
import hu.tigra.jee.data.AllocationRepository;
import hu.tigra.jee.model.Allocation;
import hu.tigra.jee.service.AllocationRegistration;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.ValidationException;
import javax.validation.Validator;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * JAX-RS Example
 * <p/>
 * This class produces a RESTful service to read/write the contents of the allocations table.
 */
@Path("/allocations")
@RequestScoped
public class AllocationResourceRESTService {

    @Inject
    AllocationRegistration registration;
    @Inject
    private Logger log;
    @Inject
    private Validator validator;
    @Inject
    private AllocationRepository repository;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Allocation> listAllAllocations() {
        return repository.findAllOrderedByStart();
    }

    @GET
    @Path("/{id:[0-9][0-9]*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Allocation lookupAllocationById(@PathParam("id") long id) {
        Allocation allocation = repository.findById(id);
        if (allocation == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return allocation;
    }

    /**
     * Creates a new allocation from the values provided. Performs validation, and will return a JAX-RS response with either 200 ok,
     * or with a map of fields, and related errors.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createAllocation(Allocation allocation) {

        Response.ResponseBuilder builder = null;

        try {
            // Validates allocation using bean validation
            validateAllocation(allocation);

            registration.register(allocation);

            // Create an "ok" response
            builder = Response.ok();
        } catch (ConstraintViolationException ce) {
            // Handle bean validation issues
            builder = createViolationResponse(ce.getConstraintViolations());
        } /*catch (ValidationException e) {
            // Handle the unique constrain violation
            Map<String, String> responseObj = new HashMap<>();
            responseObj.put("email", "Email taken");
            builder = Response.status(Response.Status.CONFLICT).entity(responseObj);
        }*/ catch (Exception e) {
            // Handle generic exceptions
            Map<String, String> responseObj = new HashMap<>();
            responseObj.put("error", e.getMessage());
            builder = Response.status(Response.Status.BAD_REQUEST).entity(responseObj);
        }

        return builder.build();
    }

    /**
     * <p>
     * Validates the given Allocation variable and throws validation exceptions based on the type of error. If the error is standard
     * bean validation errors then it will throw a ConstraintValidationException with the set of the constraints violated.
     * </p>
     * <p>
     * If the error is caused because an existing allocation with the same email is registered it throws a regular validation
     * exception so that it can be interpreted separately.
     * </p>
     *
     * @param allocation Allocation to be validated
     * @throws ConstraintViolationException If Bean Validation errors exist
     * @throws ValidationException If allocation with the same email already exists
     */
    private void validateAllocation(Allocation allocation) throws Exception {
        // Create a bean validator and check for issues.
        Set<ConstraintViolation<Allocation>> violations = validator.validate(allocation);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(new HashSet<ConstraintViolation<?>>(violations));
        }
        //ez nem működik
        if (timeDiff(allocation.getStart(), allocation.getEnd())) {
            throw new ValidationException("RESTMin. 15 perc foglalas!");
        }

        //ez nem működik
        if (isCollision(allocation.getStart(), allocation.getEnd())) {
            throw new ValidationException("Utkozes!");
        }
        //nem működik
        if (isCollisionMadeInKokany(allocation)) {
            throw new ValidationException("Időpont ütközés");
        }

    }

    /**
     * Creates a JAX-RS "Bad Request" response including a map of all violation fields, and their message. This can then be used
     * by clients to show violations.
     * 
     * @param violations A set of violations that needs to be reported
     * @return JAX-RS response containing all violations
     */
    private Response.ResponseBuilder createViolationResponse(Set<ConstraintViolation<?>> violations) {
        log.fine("Validation completed. violations found: " + violations.size());

        Map<String, String> responseObj = new HashMap<>();

        for (ConstraintViolation<?> violation : violations) {
            responseObj.put(violation.getPropertyPath().toString(), violation.getMessage());
        }

        return Response.status(Response.Status.BAD_REQUEST).entity(responseObj);
    }


    public boolean timeDiff(Date dateStart, Date dateStop) {

        boolean igaz = false;

        long diff = dateStop.getTime() - dateStart.getTime();
        log.info("" + diff);
        long diffMinutes = diff / (60 * 1000) % 60;

        if (diffMinutes < 15) {
            igaz = true;
        }
        return igaz;
    }

    public boolean isCollision(Date start, Date end) {
        Allocation allocation = null;
        try {
            allocation = repository.findCollision(start, end);
        } catch (NoResultException e) {
            // ignore
        }
        return allocation != null;
    }

    public boolean isCollisionMadeInKokany(Allocation newA) {
        boolean result = false;
        List<Allocation> allocations = repository.findAllOrderedByStart();
        long newAllStart = newA.getStart().getTime();
        long newAllEnd = newA.getEnd().getTime();
        long listAllStart, listAllEnd;

        for (int i = 0; i <= allocations.size(); i++) {
            listAllStart = allocations.get(i).getStart().getTime();
            listAllEnd = allocations.get(i).getEnd().getTime();
            if ((newAllStart >= listAllStart && newAllStart <= listAllEnd) || (newAllEnd >= listAllStart && newAllEnd <= listAllEnd)) {
                result = true;
            }
        }
        return result;
    }
}
