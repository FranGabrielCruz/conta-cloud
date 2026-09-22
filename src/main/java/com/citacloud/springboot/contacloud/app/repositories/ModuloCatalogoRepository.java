package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.ModuloCatalogo;
import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface ModuloCatalogoRepository extends JpaRepository<ModuloCatalogo,String>{List<ModuloCatalogo> findAllByActiveTrueAndImplementedTrueOrderByDisplayOrder();}
