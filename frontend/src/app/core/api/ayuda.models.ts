export interface AyudaArticulo {
  id: number;
  idCategoria: number;
  pregunta: string;
  respuesta: string;
}

export interface AyudaCategoria {
  id: number;
  nombre: string;
  slug: string;
  articulos: AyudaArticulo[];
}
