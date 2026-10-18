-- Servicios de demostracion para las primeras canchas activas sin atributos,
-- de modo que la seccion "Servicios incluidos" no aparezca vacia en la demo.
INSERT IGNORE INTO cancha_caracteristica (id_cancha, id_caracteristica, valor)
SELECT c.id_cancha, ch.id_caracteristica, NULL
FROM cancha c
JOIN caracteristica ch ON ch.nombre IN ('Vestuarios', 'Duchas', 'Estacionamiento', 'Iluminación nocturna')
WHERE c.estado = 'ACTIVA'
  AND NOT EXISTS (SELECT 1 FROM cancha_caracteristica cc WHERE cc.id_cancha = c.id_cancha)
ORDER BY c.id_cancha
LIMIT 240;
