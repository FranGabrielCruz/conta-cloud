package com.citacloud.springboot.contacloud.app.controllers;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.multitenancy.TenantRoutingContext;
import com.citacloud.springboot.contacloud.app.security.PrintAgentPrincipal;
import com.citacloud.springboot.contacloud.app.services.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/print-agents")
public class PrintAgentController {
    private final PrintAgentPairingService service;private final PrintAgentRateLimiter limiter;private final PrintAgentTokenService tokens;private final boolean requireHttps;
    public PrintAgentController(PrintAgentPairingService service,PrintAgentRateLimiter limiter,PrintAgentTokenService tokens,@Value("${contacloud.print-agents.require-https:true}")boolean requireHttps){this.service=service;this.limiter=limiter;this.tokens=tokens;this.requireHttps=requireHttps;}
    @PostMapping("/pair") public TokenAgenteImpresionDto pair(@Valid @RequestBody PairingAgenteImpresionInput input,HttpServletRequest request){secure(request);limiter.verificar(request.getRemoteAddr());return service.vincular(input);}
    @PostMapping("/refresh") public TokenAgenteImpresionDto refresh(@Valid @RequestBody RenovarAgenteImpresionInput input,HttpServletRequest request){secure(request);limiter.verificar(request.getRemoteAddr());var tenant=tokens.tenantDelToken(input.refreshToken());try(var ignored=TenantRoutingContext.use(tenant)){return service.renovar(input.refreshToken());}}
    @GetMapping("/me") public IdentidadAgenteImpresionDto me(Authentication authentication){return service.identidad(principal(authentication));}
    @PostMapping("/heartbeat") public ResponseEntity<Void> heartbeat(@Valid @RequestBody(required=false) HeartbeatAgenteImpresionInput input,Authentication authentication){service.heartbeat(principal(authentication),input==null?null:input.applicationVersion());return ResponseEntity.noContent().build();}
    private void secure(HttpServletRequest request){if(requireHttps&&!request.isSecure())throw new VinculacionAgenteException();}
    private static PrintAgentPrincipal principal(Authentication auth){if(auth==null||!(auth.getPrincipal() instanceof PrintAgentPrincipal p))throw new VinculacionAgenteException();return p;}
}
