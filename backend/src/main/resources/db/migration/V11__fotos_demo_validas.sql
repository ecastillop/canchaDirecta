-- Sustituye URLs de demostracion que apuntaban a un dominio inexistente
-- por imagenes publicas validas, para que las miniaturas se vean en la demo.
UPDATE foto_cancha
SET url = CONCAT('https://picsum.photos/seed/canchadirecta-', id_foto, '/800/600')
WHERE url LIKE 'https://cdn.canchadirecta.pe/%';
