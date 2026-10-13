UPDATE warehouse
SET system_managed=TRUE,warehouse_type='TRANSITO',active=TRUE
WHERE code='SYS-TRANSIT';
