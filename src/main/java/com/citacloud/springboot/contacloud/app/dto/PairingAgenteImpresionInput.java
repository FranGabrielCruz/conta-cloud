package com.citacloud.springboot.contacloud.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record PairingAgenteImpresionInput(@NotBlank @Pattern(regexp="[0-9]{6}") String pairingCode,
    @NotNull @Valid Dispositivo deviceInformation){
    public record Dispositivo(@Size(max=150) String machineName,@Size(max=60) String applicationVersion,
        @Size(max=150) String operatingSystem){}
}
