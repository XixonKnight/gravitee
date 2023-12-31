/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.management.rest.resource;

import io.gravitee.common.http.MediaType;
import io.gravitee.management.model.ApplicationEntity;
import io.gravitee.management.model.NewApplicationEntity;
import io.gravitee.management.model.application.ApplicationListItem;
import io.gravitee.management.model.application.ApplicationSettings;
import io.gravitee.management.model.application.SimpleApplicationSettings;
import io.gravitee.management.model.permissions.RolePermission;
import io.gravitee.management.model.permissions.RolePermissionAction;
import io.gravitee.management.rest.security.Permission;
import io.gravitee.management.rest.security.Permissions;
import io.gravitee.management.service.ApplicationService;
import io.gravitee.management.service.notification.ApplicationHook;
import io.gravitee.management.service.notification.Hook;
import io.swagger.annotations.*;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
@Path("/applications")
@Api(tags = {"Applications"})
public class ApplicationsResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private ApplicationService applicationService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "List all the applications accessible to authenticated user",
            notes = "User must have MANAGEMENT_APPLICATION[READ] and PORTAL_APPLICATION[READ] permission to list applications.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "User's applications", response = ApplicationEntity.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_APPLICATION, acls = RolePermissionAction.READ),
            @Permission(value = RolePermission.PORTAL_APPLICATION, acls = RolePermissionAction.READ)
    })
    public List<ApplicationListItem> listApplications(
            @QueryParam("group") final String group,
            @QueryParam("query") final String query) {
        Set<ApplicationListItem> applications;

        if (query != null && !query.trim().isEmpty()) {
            applications = applicationService.findByName(query);
        } else if (isAdmin()) {
            applications = group != null
                    ? applicationService.findByGroups(Collections.singletonList(group))
                    : applicationService.findAll();
        } else {
            applications = applicationService.findByUser(getAuthenticatedUser());
            if (group != null && !group.isEmpty()) {
                applications = applications.stream()
                        .filter(app -> app.getGroups() != null && app.getGroups().contains(group))
                        .collect(Collectors.toSet());
            }
        }

        return applications.stream()
                .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                .collect(Collectors.toList());
    }

    /**
     * Create a new application for the authenticated user.
     *
     * @param application
     * @return
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Create an application",
            notes = "User must have MANAGEMENT_APPLICATION[CREATE] permission to create an application.")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Application successfully created", response = ApplicationEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_APPLICATION, acls = RolePermissionAction.CREATE),
    })
    public Response createApplication(
            @ApiParam(name = "application", required = true)
            @Valid @NotNull(message = "An application must be provided") final NewApplicationEntity application) {
        // To preserve backward compatibility, ensure that we have at least default settings for simple application type
        if (application.getSettings() == null ||
                (application.getSettings().getoAuthClient() == null && application.getSettings().getApp() == null)) {
            ApplicationSettings settings = new ApplicationSettings();
            SimpleApplicationSettings simpleAppSettings = new SimpleApplicationSettings();
            simpleAppSettings.setType(application.getType());
            simpleAppSettings.setClientId(application.getClientId());
            settings.setApp(simpleAppSettings);
            application.setSettings(settings);
        }

        ApplicationEntity newApplication = applicationService.create(application, getAuthenticatedUser());
        if (newApplication != null) {
            return Response
                    .created(URI.create("/applications/" + newApplication.getId()))
                    .entity(newApplication)
                    .build();
        }

        return Response.serverError().build();
    }

    @GET
    @Path("/hooks")
    @ApiOperation("Get the list of available hooks")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List of hooks"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Produces(MediaType.APPLICATION_JSON)
    public Hook[] getHooks() {
        return ApplicationHook.values();
    }

    @Path("{application}")
    public ApplicationResource getApplicationResource() {
        return resourceContext.getResource(ApplicationResource.class);
    }
}
