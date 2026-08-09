package com.dashboard.v1.controller;

import com.dashboard.v1.AppProperties;
import com.dashboard.v1.entity.*;
import com.dashboard.v1.model.request.CreateProjectRequest;
import com.dashboard.v1.model.request.ProjectVendorLinksRequest;
import com.dashboard.v1.model.response.ProjectTableDataResponse;
import com.dashboard.v1.model.response.VendorLinks;
import com.dashboard.v1.model.response.VendorProjectDetailsResponse;
import com.dashboard.v1.repository.ClientRepository;
import com.dashboard.v1.repository.ProjectRepository;
import com.dashboard.v1.repository.SecurityTerminateFlagRepository;
import com.dashboard.v1.repository.UserRepository;
import com.dashboard.v1.service.VendorProjectDetailsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    private final ProjectRepository projectRepository;

    private final SecurityTerminateFlagRepository securityTerminateFlagRepository;

    private final UserRepository userRepository;

    private final VendorProjectDetailsService vendorProjectDetailsService;

    private final ClientRepository clientRepository;

    private final AppProperties appProperties;

    /**
     * Generates a unique, small token from the projectIdentifier
     * Uses SHA-256 hash and takes first 10 characters for a short, unique token
     *
     * @param projectIdentifier The original project identifier
     * @return A unique 10-character token
     */
    private String generateProjectIdentifierToken(String projectIdentifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(projectIdentifier.getBytes(StandardCharsets.UTF_8));

            // Convert to hexadecimal and take first 10 characters
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(5, hash.length); i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString().toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            logger.error("Error generating project identifier token", e);
            // Fallback: use UUID-based token if SHA-256 fails
            return UUID.randomUUID().toString().substring(0, 10).replace("-", "");
        }
    }

    @PostMapping("/create/project")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> createProject(@RequestBody CreateProjectRequest request) {
        logger.info("inside ProjectController /create/project ");
        Map<String, Object> response = new HashMap<>();
        try {
            // Validate project identifier is unique
            if (request.getProjectIdentifier() == null || request.getProjectIdentifier().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Project identifier is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Check if project identifier already exists (must be unique)
            Optional<Project> existingProject = projectRepository.findByProjectIdentifier(request.getProjectIdentifier());
            if (existingProject.isPresent()) {
                response.put("success", false);
                response.put("message", "Project identifier already exists. Please use a unique identifier.");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }

            // Find the client by username
            Optional<Client> clientOpt = clientRepository.findByUsername(request.getClientUsername());
            if (!clientOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "Client does not exist");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            Client client = clientOpt.get();

            // Create new project
            Project project = new Project();
            project.setProjectIdentifier(request.getProjectIdentifier());
            project.setProjectIdentifierToken(generateProjectIdentifierToken(request.getProjectIdentifier()));
            project.setCountryLinks(request.getCountryLinks());
            project.setIr(request.getIr());
            project.setCounts(request.getCounts());
            project.setLoi(request.getLoi());
            project.setQuota(request.getQuota());
            project.setCpi(request.getCpi());

            // Save the project first (this will set the client_id foreign key)
            Project savedProject = projectRepository.save(project);

            SecurityTerminateFlag securityTerminateFlag = new SecurityTerminateFlag();
            securityTerminateFlag.setProjectId(request.getProjectIdentifier());
            securityTerminateFlag.setFlag(request.getSecurityTerminateFlag());

            securityTerminateFlagRepository.save(securityTerminateFlag);


            // Update the client's projects list (inverse side of relationship)
            if (client.getProjects() == null) {
                client.setProjects(new ArrayList<>());
            }
            client.getProjects().add(savedProject.getProjectIdentifier());
            clientRepository.save(client);  // Update client to maintain bidirectional consistency

            logger.info("Project created successfully: {} for client: {}",
                       savedProject.getProjectIdentifier(), client.getUsername());

            response.put("success", true);
            response.put("message", "Project created successfully!");
            response.put("projectId", savedProject.getId());
            response.put("projectIdentifier", savedProject.getProjectIdentifier());
            response.put("clientUsername", client.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating project: ", e);
            response.put("success", false);
            response.put("message", "Something went wrong! " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> getAllProjects(
            @RequestParam(required = false, defaultValue = "ACTIVE") ProjectStatus projectStatus) {
        logger.info("inside ProjectController /projects/all with status: {}", projectStatus);
        try {
            // Use JOIN FETCH to eagerly load client within transaction
            List<Project> projects = projectRepository.findAllWithClient(projectStatus);

            logger.info("Fetched {} projects with status {}", projects.size(), projectStatus);

            // Return the projects - @JsonIgnoreProperties will handle serialization
            return ResponseEntity.ok(projects);
        } catch (Exception e) {
            logger.error("Error fetching projects: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Collections.singletonMap("error", "Failed to fetch projects: " + e.getMessage()));
        }
    }

    @GetMapping("/table-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> getProjects() {
        logger.info("inside ProjectController /projects/table-data ");
        try {
            // Fetch all projects
            List<Project> projects = projectRepository.findAllProjectsWithoutClient();

            // Map to DTO to avoid infinite recursion from bidirectional relationship
            List<ProjectTableDataResponse> response = new ArrayList<>();
            for (Project project : projects) {
                ProjectTableDataResponse dto = new ProjectTableDataResponse();
                dto.setProjectIdentifier(project.getProjectIdentifier());
                dto.setStatus(project.getStatus());
                dto.setComplete(project.getComplete());
                dto.setTerminate(project.getTerminate());
                dto.setQuotafull(project.getQuotafull());
                dto.setSecurityTerminate(project.getSecurityTerminate());
                dto.setCounts(project.getCounts());
                dto.setVendorsUsername(project.getVendorsUsername());
                dto.setIr(project.getIr());
                dto.setLoi(project.getLoi());
                dto.setQuota(project.getQuota());
                dto.setCpi(project.getCpi());
                response.add(dto);
            }

            logger.info("Fetched {} projects for table data", response.size());

             return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching projects: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Failed to fetch projects: " + e.getMessage()));
        }
    }

    @PostMapping("/vendor-list")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<List<VendorLinks>> getProjectVendorLinks(@RequestBody ProjectVendorLinksRequest request) {
        logger.info("inside ProjectController /projects/vendor-list ");
        try {

            Optional<Project> project = projectRepository.findByProjectIdentifier(request.getProjectId());

            if (!project.isPresent()) return ResponseEntity.ok(Collections.emptyList());

            List<VendorLinks> vendorLinks = new ArrayList<>();

            List<String> vendorIds = project.get().getVendorsUsername();

            vendorIds.forEach(vendorId -> {
                Optional<User> vendorOptional = userRepository.findByUsername(vendorId);
                if (!vendorOptional.isPresent()) {
                    logger.warn("Vendor not found: {}", vendorId);
                    return;
                }

                User vendor = vendorOptional.get();

                project.get().getCountryLinks().forEach(countrylink ->
                {
                    VendorLinks link = new VendorLinks();
                    String pidToken = project.get().getProjectIdentifierToken();
                    if(pidToken == null || pidToken.isEmpty()) {
                        // Generate and set token if not present
                        pidToken = generateProjectIdentifierToken(project.get().getProjectIdentifier());
                        project.get().setProjectIdentifierToken(pidToken);
                        projectRepository.save(project.get());
                    }
                    link.setVendorName(vendor.getUsername());
                    link.setLink(appProperties.getDomain() + "/survey/" + vendor.getUserToken() + "/" + countrylink.getCountry() + "?PID=" + project.get().getProjectIdentifierToken() + "&UID=111");
                    vendorLinks.add(link);
                });

            });
            return ResponseEntity.ok(vendorLinks);
        } catch (Exception e) {
            logger.error("Error fetching projects: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.emptyList());
        }
    }

    // ✅ Fetch projects assigned to vendor (Vendor only)
    @GetMapping("/vendor")
    public List<VendorProjectDetailsResponse> getVendorProjects(@RequestParam String username) {
        logger.info("inside ProjectController /projects/vendor ");
        return vendorProjectDetailsService.getProjectsDetails(username);
    }

    // ✅ Fetch full project details (Admin can view any project)
    @GetMapping("/{projectId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> getProjectDetails(@PathVariable Long projectId) {
        logger.info("inside ProjectController /projects/{projectId} projectId : {} ", projectId);
        try {
            Optional<Project> projectOpt = projectRepository.findById(projectId);

            if (projectOpt.isPresent()) {
                Project project = projectOpt.get();

                return ResponseEntity.ok(project);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("error", "Project not found"));
            }
        } catch (Exception e) {
            logger.error("Error fetching project details for projectId: {}", projectId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Collections.singletonMap("error", "Failed to fetch project: " + e.getMessage()));
        }
    }

    // ✅ Update project status by projectId
    @GetMapping("/status/update/{projectId}")
    @Transactional
    public ResponseEntity<?> updateProjectStatus(
            @PathVariable String projectId,
            @RequestParam ProjectStatus status) {
        logger.info("inside ProjectController /status/update/{projectId} projectId: {}, new status: {}", projectId, status);

        try {
            // Use query without client to avoid lazy loading issues
            Optional<Project> optionalProject = projectRepository.findByProjectIdentifierWithoutClient(projectId);

            if (optionalProject.isPresent()) {
                Project project = optionalProject.get();

                // Set the status from request parameter
                project.setStatus(status);
                projectRepository.save(project);

                logger.info("Project status updated: {} - New status: {}", projectId, status);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("status", status.toString());
                response.put("message", "Project status updated successfully");
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("error", "Project not found"));
            }
        } catch (Exception e) {
            logger.error("Error updating project status for projectId: {}", projectId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Collections.singletonMap("error", "Failed to update project status: " + e.getMessage()));
        }

    }

    // ✅ Update project counts by projectId
    @GetMapping("/counts/update/{projectId}")
    @Transactional
    public ResponseEntity<?> updateProjectCounts(
            @PathVariable String projectId,
            @RequestParam Long counts) {
        logger.info("inside ProjectController /counts/update/{projectId} projectId: {}, new counts: {}", projectId, counts);

        try {
            // Validate counts is not negative
            if (counts < 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", "Counts cannot be negative"));
            }

            Optional<Project> optionalProject = projectRepository.findByProjectIdentifierWithoutClient(projectId);

            if (optionalProject.isPresent()) {
                Project project = optionalProject.get();

                // Set the counts from request parameter
                project.setCounts(counts);
                projectRepository.save(project);

                logger.info("Project counts updated: {} - New counts: {}", projectId, counts);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("counts", counts);
                response.put("message", "Project counts updated successfully");
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("error", "Project not found"));
            }
        } catch (Exception e) {
            logger.error("Error updating project counts for projectId: {}", projectId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Collections.singletonMap("error", "Failed to update project counts: " + e.getMessage()));
        }
    }

    // ✅ Delete project by projectId
    @DeleteMapping("/delete/{projectId}")
    @Transactional
    public ResponseEntity<?> deleteProject(@PathVariable String projectId) {
        logger.info("inside ProjectController /delete/{projectId} projectId: {}", projectId);

        try {
            Optional<Project> optionalProject = projectRepository.findByProjectIdentifier(projectId);

            if (optionalProject.isPresent()) {
                Project project = optionalProject.get();

                // Delete the project
                projectRepository.delete(project);

                logger.info("Project deleted successfully: {}", projectId);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Project deleted successfully");
                response.put("projectId", projectId);
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("error", "Project not found"));
            }
        } catch (Exception e) {
            logger.error("Error deleting project for projectId: {}", projectId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Collections.singletonMap("error", "Failed to delete project: " + e.getMessage()));
        }
    }

}
