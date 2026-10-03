package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.TipoProducto;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ProductCodeService {
    private final EntityManager entityManager;
    public ProductCodeService(EntityManager entityManager){this.entityManager=entityManager;}
    public String next(UUID tenant,UUID empresa,TipoProducto tipo){String column=tipo==TipoProducto.SERVICE?"service_last_number":"product_last_number";
        Object value=entityManager.createNativeQuery("""
            INSERT INTO product_sequence(tenant_id,empresa_id,product_last_number,service_last_number)
            VALUES (:tenant,:empresa,%s,%s)
            ON CONFLICT (tenant_id,empresa_id) DO UPDATE SET %s=product_sequence.%s+1
            RETURNING %s
            """.formatted(tipo==TipoProducto.PRODUCT?"1":"0",tipo==TipoProducto.SERVICE?"1":"0",column,column,column))
            .setParameter("tenant",tenant).setParameter("empresa",empresa).getSingleResult();
        return (tipo==TipoProducto.SERVICE?"SRV-":"PRD-")+"%06d".formatted(((Number)value).longValue());}
}
