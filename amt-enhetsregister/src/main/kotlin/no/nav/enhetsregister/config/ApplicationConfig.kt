package no.nav.enhetsregister.config

import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import no.nav.common.job.leader_election.ShedLockLeaderElectionClient
import no.nav.common.rest.filter.LogRequestFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@Configuration(proxyBeanMethods = false)
class ApplicationConfig {
    @Bean
    fun logFilterRegistrationBean() = FilterRegistrationBean<LogRequestFilter>().apply {
        setFilter(LogRequestFilter("amt-enhetsregister", false))
        order = 1
        addUrlPatterns("/*")
    }

    @Bean
    fun leaderElectionClient(jdbcTemplate: JdbcTemplate) = ShedLockLeaderElectionClient(JdbcTemplateLockProvider(jdbcTemplate))
}
