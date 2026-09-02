package delivery.portal.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccessRequestUiController {

    @GetMapping("/request-access")
    public String requestAccess(Model model) {
        model.addAttribute("error", null);
        model.addAttribute("ok", null);
        return "request-access";
    }
}
