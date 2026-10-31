package com.citacloud.springboot.contacloud.app.services;

import java.time.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PrintAgentRateLimiter {
    private final int limite; private final Duration ventana; private final Clock clock; private final ConcurrentHashMap<String,Bucket> buckets=new ConcurrentHashMap<>();
    @Autowired public PrintAgentRateLimiter(@Value("${contacloud.print-agents.pairing-rate-limit:10}")int limite,@Value("${contacloud.print-agents.pairing-rate-window:PT1M}")Duration ventana){this(limite,ventana,Clock.systemUTC());}
    PrintAgentRateLimiter(int limite,Duration ventana,Clock clock){this.limite=limite;this.ventana=ventana;this.clock=clock;}
    public void verificar(String origen){Instant now=clock.instant();Bucket bucket=buckets.compute(origen==null?"unknown":origen,(k,b)->b==null||now.isAfter(b.inicio.plus(ventana))?new Bucket(now,1):new Bucket(b.inicio,b.intentos+1));if(bucket.intentos>limite)throw new VinculacionAgenteException();if(buckets.size()>10_000)buckets.entrySet().removeIf(e->now.isAfter(e.getValue().inicio.plus(ventana)));}
    private record Bucket(Instant inicio,int intentos){}
}
