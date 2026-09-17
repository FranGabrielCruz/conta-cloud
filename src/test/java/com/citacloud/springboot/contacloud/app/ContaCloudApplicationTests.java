package com.citacloud.springboot.contacloud.app;

import com.citacloud.springboot.contacloud.app.dto.EmpresaDto;
import com.citacloud.springboot.contacloud.app.mappers.EmpresaMapper;
import com.citacloud.springboot.contacloud.app.views.MainLayout;
import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ContaCloudApplicationTests {
    @Test void mapeaEmpresaSinExponerCamposTecnicos() {
        var mapper = new EmpresaMapper();
        EmpresaDto dto = new EmpresaDto(null, "EMPRESA01", "Empresa Demo", "101000001", "DO", "America/Santo_Domingo", true);
        assertThat(mapper.toDto(mapper.toEntity(dto)).codigo()).isEqualTo("EMPRESA01");
    }

    @Test void layoutPrincipalPermiteAccesoAUsuariosAutenticados() {
        assertThat(MainLayout.class.isAnnotationPresent(PermitAll.class)).isTrue();
    }
}
