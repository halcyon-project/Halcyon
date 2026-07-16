package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import org.pac4j.core.authorization.authorizer.DefaultAuthorizers;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.jee.filter.CallbackFilter;
import org.pac4j.jee.filter.LogoutFilter;
import org.pac4j.oidc.client.KeycloakOidcClient;
import org.pac4j.oidc.config.KeycloakOidcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 *
 * @author erich
 */

@Configuration
public class Cool {

    /** Name the admin-only filter refers to; registered on the pac4j Config (H4). */
    static final String ADMIN_AUTHORIZER = "halcyonAdmin";

    @Autowired
    private DefaultSslBundleRegistry defaultSslBundleRegistry;
    
    /** Authentication for every secured URL. See the authorizer note below. */
    @Bean
    public HalcyonSecurityFilter securityFilter(Config pac4jConfig) {
        HalcyonSecurityFilter securityFilter = new HalcyonSecurityFilter();
        securityFilter.setConfig(pac4jConfig);
        securityFilter.setClients("KeycloakOidcClient");
        // The authorizer list must be set EXPLICITLY, and "isAuthenticated" is what
        // this filter has always meant. Leaving it blank does NOT mean "no authorizer":
        // pac4j's DefaultAuthorizationChecker substitutes its DEFAULT set, which for an
        // indirect client (KeycloakOidcClient) is isAuthenticated + **csrfCheck**.
        //
        // That csrfCheck made the whole application read-only. CsrfAuthorizer demands
        // the pac4jCsrfToken as a request PARAMETER or HEADER, but pac4j issues it as an
        // **HttpOnly** cookie — so no JavaScript can read it and Wicket cannot echo it
        // back. Every Wicket ajax POST on every secured page therefore got 403, while
        // GET was untouched (csrfCheck only inspects unsafe methods). The visible
        // symptom: pages and dropdowns render perfectly, and nothing you click does
        // anything — e.g. picking a collection on ListImages left the table on its
        // initial empty query, reading as a permanent "No Records Found".
        //
        // CSRF protection is not lost: Wicket's own resource-isolation listener still
        // enforces same-origin on these requests, and it works with the browser instead
        // of against it.
        //
        // H4: still NO admin authorizer here — this filter covers the whole secured-URL
        // list, so an admin check here would demand the admin group for /sparql,
        // /about, … . The admin check gets its own, narrowly-scoped filter below.
        securityFilter.setAuthorizers(DefaultAuthorizers.IS_AUTHENTICATED);
        return securityFilter;
    }

    /**
     * H4: admin-only URLs. A second, separate registration so the {@code admin}
     * authorizer applies ONLY to {@code URLControl.getAdminURLs()}.
     */
    @Bean
    public HalcyonSecurityFilter adminSecurityFilter(Config pac4jConfig) {
        HalcyonSecurityFilter securityFilter = new HalcyonSecurityFilter();
        securityFilter.setConfig(pac4jConfig);
        securityFilter.setClients("KeycloakOidcClient");
        securityFilter.setAuthorizers(ADMIN_AUTHORIZER);
        return securityFilter;
    }

    @Bean
    public FilterRegistrationBean<HalcyonSecurityFilter> adminSecurityFilterRegistration(
            @Qualifier("adminSecurityFilter") HalcyonSecurityFilter adminSecurityFilter) {
        FilterRegistrationBean<HalcyonSecurityFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(adminSecurityFilter);
        registration.setName("KeycloakOidcClientAdmin");
        String[] adminUrls = URLControl.getAdminURLs();
        if (adminUrls.length == 0) {
            registration.setEnabled(false);
            return registration;
        }
        registration.addUrlPatterns(adminUrls);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
    
    @Bean
    public HalcyonSessionListener httpSessionListener() {
        return new HalcyonSessionListener();
    }
   
    @Bean
    public Config config() {
        final KeycloakOidcConfiguration keyconfig = new KeycloakOidcConfiguration();
        keyconfig.setClientId(HalcyonSettings.CLIENT_ID);
        keyconfig.setRealm("Halcyon");
        keyconfig.setConnectTimeout(10000);
        keyconfig.setReadTimeout(10000);    
        keyconfig.setBaseUri(HalcyonSettings.getSettings().getAuthServer());
        if (HalcyonSettings.getSettings().isHTTPS2enabled()) {
            keyconfig.setSslSocketFactory(defaultSslBundleRegistry.getBundle("server").createSslContext().getSocketFactory());
        }
        KeycloakOidcClient keycloakclient = new KeycloakOidcClient(keyconfig);
        System.out.println("HACK : "+keycloakclient);
        final Clients clients = new Clients(HalcyonSettings.getSettings().getProxyHostName()+"/callback", keycloakclient);
        Config config = new Config(clients);
        // H4: the (now group-based) admin authorizer, used only by adminSecurityFilter.
        config.addAuthorizer(ADMIN_AUTHORIZER, new CustomAuthorizer());
        return config;
    }

    @Bean
    public FilterRegistrationBean<CallbackFilter> callbackFilterRegistration(CallbackFilter callbackFilter) {
        FilterRegistrationBean<CallbackFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(callbackFilter);
        registration.addUrlPatterns("/callback");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean logoutFilter() {
        final LogoutFilter filter = new LogoutFilter(config(), "/?defaulturlafterlogout");
        filter.setDestroySession(true);
        final FilterRegistrationBean registrationBean = new FilterRegistrationBean();
        registrationBean.setFilter(filter);
        registrationBean.addUrlPatterns("/skunkworks/logout");
        return registrationBean;
    }
    
    @Bean
    public CallbackFilter callbackFilter(Config pac4jConfig) {
        CallbackFilter callbackFilter = new CallbackFilter();
        callbackFilter.setConfig(pac4jConfig);
        callbackFilter.setDefaultUrl("/");
        return callbackFilter;
    }
    
    // H4 added a SECOND HalcyonSecurityFilter bean (adminSecurityFilter), so this
    // parameter became ambiguous by type. Spring would normally fall back to
    // matching the parameter NAME against the bean name, but that needs javac
    // -parameters, which this build does not set — hence the explicit @Qualifier
    // on both registrations rather than relying on parameter-name retention.
    @Bean
    public FilterRegistrationBean<HalcyonSecurityFilter> securityFilterRegistration(
            @Qualifier("securityFilter") HalcyonSecurityFilter securityFilter) {
        FilterRegistrationBean<HalcyonSecurityFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(securityFilter);
        registration.setName("KeycloakOidcClient");
        registration.addUrlPatterns(URLControl.getSecuredURLs());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1); // Order just after CallbackFilter
        return registration;
    }
}
