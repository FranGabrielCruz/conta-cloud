package com.citacloud.springboot.contacloud.app.security;

import com.citacloud.springboot.contacloud.app.multitenancy.TenantRoutingContext;
import com.citacloud.springboot.contacloud.app.services.PrintAgentPairingService;
import com.citacloud.springboot.contacloud.app.services.PrintAgentTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PrintAgentAuthenticationFilter extends OncePerRequestFilter {
    private final PrintAgentPairingService service;
    private final PrintAgentTokenService tokens;
    public PrintAgentAuthenticationFilter(PrintAgentPairingService service,PrintAgentTokenService tokens){this.service=service;this.tokens=tokens;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){String path=request.getRequestURI();return !path.equals("/api/print-agents/me")&&!path.equals("/api/print-agents/heartbeat");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String header=request.getHeader("Authorization");
        if(header==null||!header.startsWith("Bearer ")){unauthorized(response);return;}
        String raw=header.substring(7).trim();java.util.UUID tenant;
        try{tenant=tokens.tenantDelToken(raw);}catch(RuntimeException exception){unauthorized(response);return;}
        try(var ignored=TenantRoutingContext.use(tenant)){
            PrintAgentPrincipal principal;
            try{principal=service.autenticar(raw);}catch(RuntimeException exception){SecurityContextHolder.clearContext();unauthorized(response);return;}
            service.registrarAutenticacion(principal);
            var authentication=new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority("PRINT_AGENT")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request,response);
        }finally{SecurityContextHolder.clearContext();}
    }
    private static void unauthorized(HttpServletResponse response)throws IOException{response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);response.setContentType(MediaType.APPLICATION_JSON_VALUE);response.getWriter().write("{\"code\":\"AUTHENTICATION_ERROR\",\"message\":\"No fue posible autenticar el agente.\"}");}
}
