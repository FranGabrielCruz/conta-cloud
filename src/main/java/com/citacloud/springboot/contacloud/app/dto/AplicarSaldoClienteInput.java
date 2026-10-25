package com.citacloud.springboot.contacloud.app.dto;

import java.util.List;

public record AplicarSaldoClienteInput(List<CobroClienteInput.AplicacionFactura> aplicaciones) {}
