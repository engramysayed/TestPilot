package delivery.portal.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Browsers often request /favicon.ico — serve the same mark as the sidebar tab icon. */
@Controller
public class FaviconController {
    @GetMapping("/favicon.ico")
    public String favicon() {
        return "redirect:/img/favicon.svg";
    }
}
