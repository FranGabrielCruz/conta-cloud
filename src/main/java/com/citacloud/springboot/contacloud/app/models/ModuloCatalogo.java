package com.citacloud.springboot.contacloud.app.models;
import jakarta.persistence.*;
@Entity @Table(name="module_catalog")
public class ModuloCatalogo {
    @Id @Column(name="module_key",length=60) private String key;
    @Column(nullable=false,length=120) private String name;
    @Column(length=255) private String description;
    @Column(nullable=false) private boolean active;
    @Column(nullable=false) private boolean implemented;
    @Column(nullable=false) private boolean core;
    @Column(name="display_order",nullable=false) private int displayOrder;
    @Column(name="required_module_key",length=60) private String requiredModuleKey;
    protected ModuloCatalogo(){}
    public String getKey(){return key;} public String getName(){return name;} public String getDescription(){return description;}
    public boolean isActive(){return active;} public boolean isImplemented(){return implemented;} public boolean isCore(){return core;}
    public int getDisplayOrder(){return displayOrder;} public String getRequiredModuleKey(){return requiredModuleKey;}
}
