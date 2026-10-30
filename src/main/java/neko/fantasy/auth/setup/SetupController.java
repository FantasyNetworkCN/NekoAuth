package neko.fantasy.auth.setup;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setup")
public class SetupController {

    public static final String SETUP_TOKEN_HEADER = "X-Setup-Token";

    private final SetupService setupService;

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping("/status")
    public SetupResponses.Status status() {
        return setupService.status();
    }

    @PostMapping("/install")
    public SetupResponses.Result install(
            @RequestHeader(name = SETUP_TOKEN_HEADER, required = false) String setupToken,
            @Valid @RequestBody SetupRequests.Install request
    ) {
        return setupService.install(setupToken, request);
    }
}
