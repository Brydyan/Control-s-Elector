export interface MenuNode {
  id: string;
  parentId: string | null;
  codigo: string;
  label: string;
  icono?: string;
  ruta: string;
  orden: number;
  activo: boolean;
  children: MenuNode[];
}

export interface MenuResponse {
  menus: MenuNode[];
}
