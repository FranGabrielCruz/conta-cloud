package com.citacloud.springboot.contacloud.app.views;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the actual frontend handler tests as part of Maven, without another test dependency. */
class BillingKeyboardJavascriptTest {
    @TempDir Path temporary;
    @Test void politicaDeTecladoRealPasaSuMatrizDeEventos()throws Exception{
        Path output=temporary.resolve("keyboard-tests.log");
        String node=System.getenv().getOrDefault("NODE_BINARY","node");
        Process process=new ProcessBuilder(node,"--test","src/test/frontend/billing-keyboard-shortcuts.test.mjs")
            .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if(!process.waitFor(30,TimeUnit.SECONDS)){process.destroyForcibly();fail("Las pruebas de teclado excedieron 30 segundos.");}
        assertEquals(0,process.exitValue(),()->{try{return Files.readString(output);}catch(Exception ex){return "No fue posible leer el resultado de las pruebas JavaScript.";}});
    }
}
