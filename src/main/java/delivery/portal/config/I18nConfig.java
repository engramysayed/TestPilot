package delivery.portal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.Locale;

/**
 * Portal language: English only. Earlier a cookie-backed resolver let {@code ?lang=ar}
 * flip the UI; it picked Arabic up from the browser's Accept-Language too, which showed
 * Arabic date pickers even when the chrome stayed English. Pinned to English for now.
 */
@Configuration
public class I18nConfig {

    @Bean
    public LocaleResolver localeResolver() {
        return new FixedLocaleResolver(Locale.ENGLISH);
    }
}
