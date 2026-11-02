// Rendering core mirrored from contaCloudPrint; see docs/DEVOLUCION-POS-80MM.md.
package com.citacloud.springboot.contacloud.app.reports.pos;

public interface DocumentRenderer<T> {
    byte[] render(T document);
}
