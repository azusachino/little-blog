package cn.az.blog.admin.config;

import cn.az.blog.admin.component.JwtAuthenticationTokenFilter;
import cn.az.blog.admin.component.RestfulAccessDeniedHandler;
import cn.az.blog.admin.component.RestfulAuthenticationEntryPoint;
import cn.az.blog.admin.dto.UserDetails;
import cn.az.blog.admin.entity.Permission;
import cn.az.blog.admin.entity.User;
import cn.az.blog.admin.service.IUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

/**
 * @author ycpang
 * @since 2021-01-29 16:00
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Resource
    private IUserService userService;

    @Resource
    private RestfulAccessDeniedHandler restAccessDeniedHandler;

    @Resource
    private RestfulAuthenticationEntryPoint restfulAuthenticationEntryPoint;

    @Resource
    private JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter;

    @Override
    protected void configure(HttpSecurity httpSecurity) throws Exception {
        // 由于使用的是JWT，我们这里不需要csrf
        httpSecurity.csrf()
            .disable()
            // 基于token，所以不需要session
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
            // 允许对于网站静态资源的无授权访问
            .antMatchers(HttpMethod.GET,
                "/",
                "/*.html",
                "/favicon.ico",
                "/**/*.html",
                "/**/*.css",
                "/**/*.js",
                "/swagger-resources/**",
                "/v2/api-docs/**"
            )
            .permitAll()
            // 对登录注册要允许匿名访问
            .antMatchers("/login")
            .permitAll()
            // 跨域请求会先进行一次options请求
            .antMatchers(HttpMethod.OPTIONS)
            .permitAll()
            // FIXME 测试时全部允许访问
            .antMatchers("/**")
            .permitAll()
            .anyRequest()// 除上面外的所有请求全部需要鉴权认证
            .authenticated();
        // 禁用缓存
        httpSecurity.headers().cacheControl();
        // 添加JWT filter
        httpSecurity.addFilterBefore(jwtAuthenticationTokenFilter, UsernamePasswordAuthenticationFilter.class);
        //添加自定义未授权和未登录结果返回
        httpSecurity.exceptionHandling()
            .accessDeniedHandler(restAccessDeniedHandler)
            .authenticationEntryPoint(restfulAuthenticationEntryPoint);
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService())
            .passwordEncoder(passwordEncoder());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 自定义实现userDetailService
     *
     * @return 用户服务
     */
    @Bean
    @Override
    public UserDetailsService userDetailsService() {
        //获取登录用户信息
        return username -> {
            User user = userService.getUserByUsername(username);
            if (Objects.nonNull(user)) {
                List<Permission> permissionList = userService.getPermissionList(user.getId());
                return new UserDetails(user, permissionList);
            }
            throw new UsernameNotFoundException("未找到当前用户" + username + "的信息");
        };
    }

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }
}
