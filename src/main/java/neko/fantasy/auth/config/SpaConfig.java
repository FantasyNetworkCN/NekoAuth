package neko.fantasy.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/login").setViewName("forward:/index.html");
        registry.addViewController("/register").setViewName("forward:/index.html");
        registry.addViewController("/setup").setViewName("forward:/index.html");
        registry.addViewController("/authorize").setViewName("forward:/index.html");
        registry.addViewController("/oauth/consent").setViewName("forward:/index.html");
        registry.addViewController("/account").setViewName("forward:/index.html");
        registry.addViewController("/admin").setViewName("forward:/index.html");
        registry.addViewController("/admin/clients").setViewName("forward:/index.html");
        registry.addViewController("/admin/settings").setViewName("forward:/index.html");
    }
}
