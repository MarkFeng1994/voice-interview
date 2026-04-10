package com.interview.common.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.interview.common.web.RequestTracingFilter;
import com.interview.module.user.security.JwtAuthenticationFilter;

@Configuration
public class SecurityFilterConfig {

	@Bean
	public FilterRegistrationBean<RequestTracingFilter> requestTracingFilterRegistration(
			RequestTracingFilter requestTracingFilter
	) {
		FilterRegistrationBean<RequestTracingFilter> registrationBean = new FilterRegistrationBean<>();
		registrationBean.setFilter(requestTracingFilter);
		registrationBean.addUrlPatterns("/*");
		registrationBean.setOrder(0);
		return registrationBean;
	}

	@Bean
	public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
			JwtAuthenticationFilter jwtAuthenticationFilter
	) {
		FilterRegistrationBean<JwtAuthenticationFilter> registrationBean = new FilterRegistrationBean<>();
		registrationBean.setFilter(jwtAuthenticationFilter);
		registrationBean.addUrlPatterns("/api/*");
		registrationBean.setOrder(1);
		return registrationBean;
	}
}
