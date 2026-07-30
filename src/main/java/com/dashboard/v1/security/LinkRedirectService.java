package com.dashboard.v1.security;

import com.dashboard.v1.AppProperties;
import com.dashboard.v1.entity.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.ModelAndView;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LinkRedirectService {

    private static final Logger logger = LoggerFactory.getLogger(LinkRedirectService.class);

    final AppProperties appProperties;

    public ResponseEntity<String> passedSurvey(Project project,String uid, String pid, String country) {
        // redirect to main survey link

        List<CountryLink> links = project.getCountryLinks();
        logger.debug("Project has {} country links", links != null ? links.size() : 0);

        if (links == null || links.isEmpty()) {
            logger.error("No country links found for project: {}", pid);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create("/rejection?type=INTERNAL_ERROR"))
                    .build();
        }

        // Find the first matching country and get its originalLink
        String redirectUrl = links.stream()
                .filter(link -> Objects.equals(link.getCountry().name(), country))
                .map(CountryLink::getOriginalLink)
                .findFirst()
                .orElse(null);

        if(redirectUrl == null){
            logger.error("No survey link found for country: {} in project: {}", country, pid);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create("/rejection?type=TERMINATE"))
                    .build();
        }
        logger.info("Found redirect URL for country {}: {}", country, redirectUrl);

        String url = redirectUrl.trim();

        // Step 11: Build redirect URL
        url = url.replace("[" + appProperties.getCompanyIdentifier() + "]", uid);

        logger.info("Redirecting to survey URL: {}", url);
        logger.info("========== SURVEY CLICK SUCCESS - Redirecting ==========");

        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    public ModelAndView rejectedSurvey(SurveyRejection type) {
        ModelAndView modelAndView = new ModelAndView("rejection-page");
        modelAndView.addObject("companyName", "Amigo Insight");

        switch (type) {
            case IP:
                // Wrong country IP
                logger.warn("Survey rejected: IP location mismatch");
                modelAndView.addObject("title", "Location Mismatch");
                modelAndView.addObject("icon", "🌍");
                modelAndView.addObject("iconType", "error");
                modelAndView.addObject("message",
                    "<strong>We're sorry!</strong><br><br>" +
                    "This survey is not available from your current location. " +
                    "It appears your IP address does not match the required country for this survey.");
                break;

            case TERMINATE:
                // Survey terminated
                logger.info("Survey rejected: Terminate");
                modelAndView.addObject("title", "Survey Terminated");
                modelAndView.addObject("icon", "✋");
                modelAndView.addObject("iconType", "warning");
                modelAndView.addObject("message",
                    "<strong>Thank you for your participation!</strong><br><br>" +
                    "Unfortunately, you do not qualify for this particular survey based on your responses. " +
                    "We appreciate your time and encourage you to check back for other opportunities.");
                break;

            case QUOTA_FULL:
                // Quota full
                logger.info("Survey rejected: Quota full");
                modelAndView.addObject("title", "Survey Full");
                modelAndView.addObject("icon", "📊");
                modelAndView.addObject("iconType", "info");
                modelAndView.addObject("message",
                    "<strong>This survey has reached its participation limit.</strong><br><br>" +
                    "We've received enough responses for this survey. " +
                    "Thank you for your interest! Please check back soon for new survey opportunities.");
                break;

            case PAUSED:
                // Survey paused
                logger.info("Survey rejected: Project paused");
                modelAndView.addObject("title", "Survey Temporarily Unavailable");
                modelAndView.addObject("icon", "⏸️");
                modelAndView.addObject("iconType", "warning");
                modelAndView.addObject("message",
                    "<strong>This survey is currently paused.</strong><br><br>" +
                    "The survey you're trying to access is temporarily unavailable. " +
                    "Please try again later or contact support if you believe this is an error.");
                break;

            case CLOSED:
                // Survey closed
                logger.info("Survey rejected: Project closed");
                modelAndView.addObject("title", "Survey Closed");
                modelAndView.addObject("icon", "🔒");
                modelAndView.addObject("iconType", "error");
                modelAndView.addObject("message",
                    "<strong>This survey is now closed.</strong><br><br>" +
                    "The survey period has ended and we are no longer accepting responses. " +
                    "Thank you for your interest!");
                break;

            case INTERNAL_ERROR:
                // Internal error
                logger.error("Survey rejected: Internal error");
                modelAndView.addObject("title", "Something Went Wrong");
                modelAndView.addObject("icon", "⚠️");
                modelAndView.addObject("iconType", "error");
                modelAndView.addObject("message",
                    "<strong>It's not you, it's us!</strong><br><br>" +
                    "We're experiencing technical difficulties at the moment. " +
                    "Please try again later or contact our support team if the problem persists.");
                break;

            default:
                logger.error("Survey rejected: Unknown reason");
                modelAndView.addObject("title", "Unable to Proceed");
                modelAndView.addObject("icon", "❌");
                modelAndView.addObject("iconType", "error");
                modelAndView.addObject("message",
                    "<strong>We're unable to process your request.</strong><br><br>" +
                    "Please contact support for assistance.");
                break;
        }

        return modelAndView;
    }

}
