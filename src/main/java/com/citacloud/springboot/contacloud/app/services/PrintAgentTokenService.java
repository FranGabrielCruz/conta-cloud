package com.citacloud.springboot.contacloud.app.services;

import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

@Service
public class PrintAgentTokenService {
    private final SecretKeySpec key; private final SecureRandom random;
    @Autowired public PrintAgentTokenService(@Value("${contacloud.security.print-agent-key:}")String configured,@Value("${app.storage.local.base-path:./data/contacloud}")String storage){this(load(configured,Path.of(storage).resolve(".print-agent-key")),new SecureRandom());}
    PrintAgentTokenService(byte[] master,SecureRandom random){this.key=new SecretKeySpec(derive(master),"HmacSHA256");this.random=random;}
    public String nuevoCodigoVinculacion(){return String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));}
    public String nuevoToken(UUID tenant){byte[] bytes=new byte[32];random.nextBytes(bytes);return tenant+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public UUID tenantDelToken(String token){try{int dot=token==null?-1:token.indexOf('.');if(dot<1)throw new IllegalArgumentException();return UUID.fromString(token.substring(0,dot));}catch(RuntimeException ex){throw new IllegalArgumentException("Token inválido");}}
    public String hashCodigo(String value){return hash("pairing",value);} public String hashAcceso(String value){return hash("access",value);} public String hashRenovacion(String value){return hash("refresh",value);}
    private String hash(String purpose,String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(key);return HexFormat.of().formatHex(mac.doFinal((purpose+":"+value).getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException ex){throw new IllegalStateException("No fue posible proteger la credencial.",ex);}}
    private static byte[] load(String configured,Path path){if(configured!=null&&!configured.isBlank()){try{byte[] decoded=Base64.getDecoder().decode(configured.trim());if(decoded.length>=32)return decoded;}catch(IllegalArgumentException ignored){}if(configured.length()>=32)return configured.getBytes(StandardCharsets.UTF_8);throw new IllegalStateException("PRINT_AGENT_TOKEN_KEY debe contener al menos 32 caracteres o Base64 de 32 bytes.");}try{if(Files.exists(path))return Base64.getDecoder().decode(Files.readString(path).trim());Files.createDirectories(path.getParent());byte[] key=new byte[32];new SecureRandom().nextBytes(key);try{Files.writeString(path,Base64.getEncoder().encodeToString(key),StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException race){return Base64.getDecoder().decode(Files.readString(path).trim());}return key;}catch(IOException|IllegalArgumentException ex){throw new IllegalStateException("No fue posible cargar la clave de agentes de impresión.",ex);}}
    private static byte[] derive(byte[] master){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(master,"HmacSHA256"));return mac.doFinal("contacloud-print-agent-v1".getBytes(StandardCharsets.UTF_8));}catch(GeneralSecurityException ex){throw new IllegalStateException(ex);}}
}
