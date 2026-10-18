package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="sales_payment_method")
public class MetodoPagoVenta {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(nullable=false,length=30) private String code;
    @Column(nullable=false,length=100) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private TipoMetodoPagoVenta type;
    @Column(nullable=false) private boolean active;
    @Column(name="display_order",nullable=false) private int displayOrder;
    @Column(name="requires_reference",nullable=false) private boolean requiresReference;
    @Column(name="requires_bank_account",nullable=false) private boolean requiresBankAccount;
    @Column(name="allows_change",nullable=false) private boolean allowsChange;
    @Column(name="is_cash",nullable=false) private boolean cash;
    @Column(name="is_electronic",nullable=false) private boolean electronic;
    @Column(length=40) private String icon;
    @Column(name="financial_bank_account_id") private UUID financialBankAccountId;
    @Column(name="is_default",nullable=false) private boolean defaultMethod;
    protected MetodoPagoVenta(){}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public String getCode(){return code;} public String getName(){return name;} public TipoMetodoPagoVenta getType(){return type;}
    public boolean isActive(){return active;} public int getDisplayOrder(){return displayOrder;}
    public boolean isRequiresReference(){return requiresReference;} public boolean isRequiresBankAccount(){return requiresBankAccount;}
    public boolean isAllowsChange(){return allowsChange;} public boolean isCash(){return cash;} public boolean isElectronic(){return electronic;}
    public String getIcon(){return icon;} public boolean isDefaultMethod(){return defaultMethod;}
    public UUID getFinancialBankAccountId(){return financialBankAccountId;}
}
