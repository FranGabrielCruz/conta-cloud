UPDATE sales_discount_reason
SET normalized_name = CASE name
    WHEN 'Promoción' THEN 'promocion'
    WHEN 'Autorización gerencial' THEN 'autorizacion gerencial'
    ELSE normalized_name
END,
updated_at = CURRENT_TIMESTAMP
WHERE name IN ('Promoción', 'Autorización gerencial');
