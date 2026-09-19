# Referencias visuales de ContaCloud

Esta carpeta centraliza las referencias visuales del producto. Antes de agregar una pantalla, incorporar aquí su referencia aprobada y comprobar que el resultado sea responsive y consistente con el layout principal.

## Estándar global aplicado

- Usar `AppPageHeader` para título, descripción y acción principal.
- Usar `AppActionButton` con `ActionType` y `ButtonSize`: tamaño principal 40 × 40 e icono 22 × 22; acción de Grid compacta. La acción Nuevo necesita un tooltip contextual (por ejemplo, «Nuevo usuario»).
- Usar `AppDetailSection` para datos de solo lectura en dos columnas; en móvil pasa a una. No representar detalles mediante formularios deshabilitados.
- Usar `AppPagination` para el control visual 10/25/50/100 y conectar cada cambio a una consulta paginada en el backend. El componente no carga datos por sí mismo.
- Mantener los colores, tamaños, espaciados y radios en los tokens globales de `styles.css`. El tema claro y oscuro comparten semántica de acciones.
- Mantener permisos, aislamiento por empresa, validación y auditoría en backend; un componente visual nunca sustituye esas reglas.

Los módulos que actualmente son marcadores de posición todavía no tienen Grid, formularios ni operaciones CRUD. Su paginación backend, filtros, búsqueda y pruebas E2E deben implementarse junto con los servicios reales de cada módulo; no se muestran acciones que aún no funcionan.
