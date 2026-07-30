package com.dashboard.v1;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalTemplateVariables {

    public final AppProperties appProperties;

    @ModelAttribute
    public void addGlobalAttributes(Model model) {
        model.addAttribute("companyName", appProperties.getCompanyName());
        model.addAttribute("companyIdentifier", appProperties.getCompanyIdentifier());
    }
}
