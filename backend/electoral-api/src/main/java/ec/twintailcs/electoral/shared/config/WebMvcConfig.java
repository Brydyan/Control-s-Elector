package ec.twintailcs.electoral.shared.config;

import ec.twintailcs.electoral.shared.security.RlsInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra el {@link RlsInterceptor} en la cadena de MVC. Corre antes
 * del handler, después de los filtros de Spring Security (incluido
 * {@link ec.twintailcs.electoral.shared.security.RlsAuthenticationBridge}).
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RlsInterceptor rlsInterceptor;

    public WebMvcConfig(RlsInterceptor rlsInterceptor) {
        this.rlsInterceptor = rlsInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rlsInterceptor);
    }
}