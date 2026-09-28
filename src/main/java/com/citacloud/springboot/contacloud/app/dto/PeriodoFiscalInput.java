package com.citacloud.springboot.contacloud.app.dto;

import java.time.LocalDate;

public record PeriodoFiscalInput(String nombre, LocalDate fechaInicial, LocalDate fechaFinal) {}
