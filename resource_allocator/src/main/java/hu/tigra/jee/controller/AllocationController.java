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
package hu.tigra.jee.controller;

import hu.tigra.jee.model.Allocation;
import hu.tigra.jee.service.AllocationRegistration;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Model;
import javax.enterprise.inject.Produces;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.validator.ValidatorException;
import javax.inject.Inject;
import javax.inject.Named;
import javax.validation.ValidationException;
import java.util.Date;
import java.util.logging.Logger;

// The @Model stereotype is a convenience mechanism to make this a request-scoped bean that has an
// EL name
// Read more about the @Model stereotype in this FAQ:
// http://www.cdi-spec.org/faq/#accordion6
@Model
public class AllocationController {
    @Inject
    private Logger log;

    @Inject
    private FacesContext facesContext;

    @Inject
    private AllocationRegistration allocationRegistration;

    @Produces
    @Named
    private Allocation newAllocation;

    @PostConstruct
    public void initNewAllocation() {
        newAllocation = new Allocation();
    }

    public void register() throws Exception {
        try {
            if (timeDiff(newAllocation.getStart(), newAllocation.getEnd())) {
                throw new ValidationException("Min. 15 perc foglalás!");
            }
            allocationRegistration.register(newAllocation);
            FacesMessage m = new FacesMessage(FacesMessage.SEVERITY_INFO, "Registered!", "Registration successful");
            facesContext.addMessage(null, m);
            initNewAllocation();
        } catch (Exception e) {
            String errorMessage = getRootErrorMessage(e);
            FacesMessage m = new FacesMessage(FacesMessage.SEVERITY_ERROR, errorMessage, "Registration unsuccessful");
            facesContext.addMessage(null, m);
        }
    }

    private String getRootErrorMessage(Exception e) {
        // Default to general error message that registration failed.
        String errorMessage = "Registration failed. See server log for more information";
        if (e == null) {
            // This shouldn't happen, but return the default messages
            return errorMessage;
        }

        // Start with the exception and recurse to find the root cause
        Throwable t = e;
        while (t != null) {
            // Get the message from the Throwable class instance
            errorMessage = t.getLocalizedMessage();
            t = t.getCause();
        }
        // This is the root cause message
        return errorMessage;
    }

    public boolean timeDiff(Date dateStart, Date dateStop) {

        boolean hiba = false;

        long diff = dateStop.getTime() - dateStart.getTime();
        long diffMinutes = diff / (60 * 1000) % 60;
        log.info("" + diffMinutes);
        if (diffMinutes < 15) {
            hiba = true;
        }
        return hiba;
    }

    //public boolean dateCollision()

}
