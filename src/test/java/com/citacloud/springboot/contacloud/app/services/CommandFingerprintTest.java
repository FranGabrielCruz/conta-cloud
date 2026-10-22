package com.citacloud.springboot.contacloud.app.services;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CommandFingerprintTest {
    record Line(UUID product, BigDecimal quantity) {}
    record Command(String note, List<Line> lines, Map<String,String> attributes) {}

    @Test void producesDeterministicFingerprint(){
        UUID product=UUID.randomUUID();
        var first=new Command("venta",List.of(new Line(product,new BigDecimal("1.00"))),Map.of("b","2","a","1"));
        var second=new Command("venta",List.of(new Line(product,new BigDecimal("1.0"))),Map.of("a","1","b","2"));
        assertEquals(CommandFingerprint.sha256(first),CommandFingerprint.sha256(second));
    }

    @Test void differentFunctionalCommandProducesDifferentFingerprint(){
        UUID product=UUID.randomUUID();
        assertNotEquals(CommandFingerprint.sha256(new Line(product,BigDecimal.ONE)),
            CommandFingerprint.sha256(new Line(product,BigDecimal.TWO)));
    }
}
