package com.citacloud.springboot.contacloud.app.models;
public enum MedioPagoMovimiento {
    CASH("Efectivo"), CARD("Tarjeta"), BANK_TRANSFER("Transferencia bancaria"),
    CHECK("Cheque"), CREDIT("Crédito"), WALLET("Wallet"), OTHER("Otro");
    private final String etiqueta;
    MedioPagoMovimiento(String etiqueta){this.etiqueta=etiqueta;}
    public String getEtiqueta(){return etiqueta;}
}
