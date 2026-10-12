package com.citacloud.springboot.contacloud.app.services;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class CotizacionNumeroService {
    private final EntityManager entityManager;
    public CotizacionNumeroService(EntityManager entityManager){this.entityManager=entityManager;}
    public String siguiente(UUID tenant,UUID empresa){Object value=entityManager.createNativeQuery("""
        INSERT INTO sales_quote_sequence(tenant_id,empresa_id,last_number)
        VALUES (:tenant,:empresa,1)
        ON CONFLICT (tenant_id,empresa_id)
        DO UPDATE SET last_number=sales_quote_sequence.last_number+1
        RETURNING last_number
        """).setParameter("tenant",tenant).setParameter("empresa",empresa).getSingleResult();
        return "COT-%06d".formatted(((Number)value).longValue());
    }
}
