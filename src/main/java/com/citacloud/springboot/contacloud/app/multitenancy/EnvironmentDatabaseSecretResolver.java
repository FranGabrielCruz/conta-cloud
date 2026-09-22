package com.citacloud.springboot.contacloud.app.multitenancy;
import com.citacloud.springboot.contacloud.app.services.ReglaNegocioException;
import java.util.Locale;
public class EnvironmentDatabaseSecretResolver implements DatabaseSecretResolver {
    @Override public DatabaseSecret resolve(DatabaseNode node){
        String prefix=node.secretReference().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","_");
        String url=System.getenv(prefix+"_JDBC_URL");
        if(url==null||url.isBlank())url="jdbc:postgresql://"+node.hostReference()+"/"+node.databaseName();
        String username=System.getenv(prefix+"_USERNAME"),password=System.getenv(prefix+"_PASSWORD");
        if(username==null||username.isBlank()||password==null)
            throw new ReglaNegocioException("No existe configuración segura para el nodo "+node.code()+".");
        return new DatabaseSecret(url,username,password);
    }
}
