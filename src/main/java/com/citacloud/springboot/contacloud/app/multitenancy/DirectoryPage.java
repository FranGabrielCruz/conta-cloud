package com.citacloud.springboot.contacloud.app.multitenancy;

import java.util.List;

public record DirectoryPage<T>(List<T> content, long total) {
    public DirectoryPage {
        content = List.copyOf(content);
        if (total < 0) throw new IllegalArgumentException("El total no puede ser negativo.");
    }
}
