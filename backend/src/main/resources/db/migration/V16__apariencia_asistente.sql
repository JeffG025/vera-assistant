-- El widget se ve en la web del cliente, junto a su marca: un morado ajeno que no pega con su
-- sitio es motivo suficiente para no instalarlo. Estas tres columnas son lo minimo para que
-- parezca suyo sin tocar codigo ni servir un CSS aparte.

-- Hexadecimal de 6 digitos; el CHECK evita que un valor invalido llegue a un style= del widget.
ALTER TABLE tenant ADD COLUMN asistente_color VARCHAR(7) NOT NULL DEFAULT '#4f46e5'
  CONSTRAINT tenant_color_hex CHECK (asistente_color ~ '^#[0-9a-fA-F]{6}$');

-- Un emoji en vez de una imagen: sin subida de archivos, sin alojamiento y sin una peticion
-- mas en la pagina del cliente. Cabe en el bundle y se ve igual en cualquier sitio.
ALTER TABLE tenant ADD COLUMN asistente_avatar VARCHAR(8) NOT NULL DEFAULT '💬';

-- Muchos sitios ya tienen algo pegado abajo a la derecha (carrito, cookies, otro chat).
ALTER TABLE tenant ADD COLUMN asistente_posicion VARCHAR(10) NOT NULL DEFAULT 'derecha'
  CONSTRAINT tenant_posicion CHECK (asistente_posicion IN ('derecha', 'izquierda'));
