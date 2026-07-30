package com.dashboard.v1.controller;

import com.dashboard.v1.AppProperties;
import com.dashboard.v1.entity.*;
import com.dashboard.v1.repository.ProjectRepository;
import com.dashboard.v1.repository.SecurityTerminateFlagRepository;
import com.dashboard.v1.repository.SurveyResponseRepository;
import com.dashboard.v1.repository.UserRepository;
import com.dashboard.v1.service.ProjectVendorService;
import com.dashboard.v1.service.RequestLogService;
import com.dashboard.v1.util.SslUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static com.dashboard.v1.entity.SurveyStatus.SECURITYTERMINATE;

@RestController
@RequestMapping("/survey")
@RequiredArgsConstructor
public class SurveyResponseController {

    private static final Logger logger = LoggerFactory.getLogger(SurveyResponseController.class);

    private final SurveyResponseRepository surveyResponseRepository;
    private final ProjectRepository projectRepository;
    private final SecurityTerminateFlagRepository securityTerminateFlagRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final ProjectVendorService projectVendorService;
    private final RequestLogService requestLogService;
    final AppProperties appProperties;

    @GetMapping("/complete")
    public ModelAndView submitComplete(@RequestParam String UID, HttpServletRequest request) {
        logger.info("inside SurveyResponseController /survey/complete UID : {}", UID);
        return saveSurveyResponse(UID, SurveyStatus.COMPLETE, request);
    }

    @GetMapping("/terminate")
    public ModelAndView submitTerminate(@RequestParam String UID, HttpServletRequest request) {
        logger.info("inside SurveyResponseController /survey/terminate UID : {}", UID);
        return saveSurveyResponse(UID, SurveyStatus.TERMINATE, request);
    }

    @GetMapping("/quotafull")
    public ModelAndView submitQuotaFull(@RequestParam String UID, HttpServletRequest request) {
        logger.info("inside SurveyResponseController /survey/quotafull UID : {}", UID);
        return saveSurveyResponse(UID, SurveyStatus.QUOTAFULL, request);
    }

    @GetMapping("/securityTerminate")
    public ModelAndView submitSecurityTerminate(@RequestParam String UID, HttpServletRequest request) {
        logger.info("inside SurveyResponseController /survey/securityTerminate UID : {}", UID);
        return saveSurveyResponse(UID, SECURITYTERMINATE, request);
    }

    private ModelAndView saveSurveyResponse(String UID, SurveyStatus status, HttpServletRequest request) {
        // Validate the project exists.

        Optional<SurveyResponse> surveyResponse = surveyResponseRepository.findByUId(UID);

        if(!surveyResponse.isPresent()){
            return null;
        }
        SurveyResponse res = surveyResponse.get();

        Project project = projectRepository.findByProjectIdentifier(surveyResponse.get().getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        SecurityTerminateFlag flag = securityTerminateFlagRepository.findByProjectId(project.getProjectIdentifier());
        Optional<User> vendor = userRepository.findByUsername(res.getVendorUsername());

        if(!(surveyResponse.get().getStatus() == SurveyStatus.IN_PROGRESS)) return null;

        // check for ip address change
        String ipAddress = requestLogService.getClientIpAddress(request);

        if(!res.getIpAddress().equals(ipAddress) && flag != null && flag.getFlag()){
            status = SECURITYTERMINATE;
            logger.info("IP address changed for UID {}: original {}, new {}", UID, res.getIpAddress(), ipAddress);
        }

        res.setStatus(status);
        res.setEndTime(ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDateTime());
        surveyResponseRepository.save(res);

        // Update project counts based on survey status
        switch (status) {
            case COMPLETE:
                Long completeCount = project.getComplete();
                project.setComplete(completeCount + 1);
                logger.info("Incremented complete count for project {} to {}", project.getProjectIdentifier(), completeCount + 1);
                projectRepository.save(project);
                break;

            case TERMINATE:
                Long terminateCount = project.getTerminate();
                project.setTerminate(terminateCount + 1);
                logger.info("Incremented terminate count for project {} to {}", project.getProjectIdentifier(), terminateCount + 1);
                projectRepository.save(project);
                break;

            case QUOTAFULL:
                Long quotaFullCount = project.getQuotafull();
                project.setQuotafull(quotaFullCount + 1);
                logger.info("Incremented quota full count for project {} to {}", project.getProjectIdentifier(), quotaFullCount + 1);
                projectRepository.save(project);
                break;

            case SECURITYTERMINATE:
                Long securityTerminateCount = project.getSecurityTerminate();
                project.setSecurityTerminate(securityTerminateCount + 1);
                logger.info("Incremented security terminate count for project {} to {}", project.getProjectIdentifier(), securityTerminateCount + 1);
                projectRepository.save(project);
                break;

            default:
                logger.warn("Unhandled survey status: {} for project {}", status, project.getProjectIdentifier());
                break;
        }

        projectVendorService.incrementSurveyCount(res.getVendorUsername(), res.getProjectId(), status);

        notifyVendorWithUid(vendor.get(), status, UID);

        return renderSurveyStatusPage(UID, status, request);
    }

    private ModelAndView renderSurveyStatusPage(String UID, SurveyStatus status, HttpServletRequest request) {
        Optional<SurveyResponse> surveyResponse = surveyResponseRepository.findByUId(UID);
        String projectId = surveyResponse.map(SurveyResponse::getProjectId).orElse("");
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(java.time.LocalDateTime.now());
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null) ipAddress = request.getRemoteAddr();
        String backgroundImage;
        switch (status) {
            case COMPLETE:
                backgroundImage = "complete.png";
                break;
            case TERMINATE:
            case SECURITYTERMINATE:
                backgroundImage = "terminate.png";
                break;
            case QUOTAFULL:
                backgroundImage = "quota-full.png";
                break;
            default:
                backgroundImage = "default.png";
        }
        ModelAndView mav = new ModelAndView("survey-status");
        mav.addObject("uid", UID);
        mav.addObject("projectId", projectId);
        mav.addObject("timestamp", timestamp);
        mav.addObject("ipAddress", ipAddress);
        mav.addObject("backgroundImage", backgroundImage);
        mav.addObject("status", status);
        return mav;
    }

    @GetMapping("/api/survey-responses/all")
    public List<SurveyResponse> getAllSurveyResponses() {
        return surveyResponseRepository.findAllOrderByCreatedAt();
    }

    private String getVendorApiUrl(User vendor, SurveyStatus status) {
        switch (status) {
            case COMPLETE:
                return vendor.getComplete();
            case TERMINATE:
                return vendor.getTerminate();
            case QUOTAFULL:
                return vendor.getQuotafull();
            case SECURITYTERMINATE:
                return vendor.getSecurityTerminate();
            default:
                return null;
        }
    }

    public ResponseEntity<String> notifyVendorWithUid(User vendor, SurveyStatus status, String UID) {
        String vendorApiUrl = getVendorApiUrl(vendor, status);

        if (vendorApiUrl == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("No Vendor Redirects configured for status: " + status);
        }

        vendorApiUrl = vendorApiUrl.replace("[" + appProperties.getCompanyIdentifier() + "]", UID);

         logger.info("Notifying vendor at URL: {}", vendorApiUrl);

        try {
            SslUtil.disableSslVerification();

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(vendorApiUrl);
            ResponseEntity<String> response = restTemplate.getForEntity(builder.toUriString(), String.class);

            return ResponseEntity.ok("Vendor notified successfully: " + response.getBody());

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to notify vendor: " + e.getMessage());
        }
    }

}
