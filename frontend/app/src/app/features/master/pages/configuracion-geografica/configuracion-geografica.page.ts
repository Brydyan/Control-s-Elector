import { CommonModule } from '@angular/common';
import {
  Component,
  OnInit,
  Type,
  computed,
  inject,
  signal,
} from '@angular/core';
import { MenuItem, PrimeTemplate, TreeNode } from 'primeng/api';
import { ContextMenuModule } from 'primeng/contextmenu';
import { DialogService } from 'primeng/dynamicdialog';
import { TreeModule } from 'primeng/tree';

import { ToastService } from '../../../../core/services/toast.service';
import {
  ButtonComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  LoadingStateComponent,
} from '../../../../shared/ui';
import { GeografiaApi } from '../../data/geografia.api';
import {
  CantonNodoDto,
  ParroquiaNodoDto,
  ProvinciaNodoDto,
} from '../../domain/models/geografia.model';
import { ConfirmDialogComponent } from '../configuracion/dialogs/confirm-dialog/confirm-dialog.component';
import { CantonModalComponent } from './dialogs/canton-modal/canton-modal.component';
import { ParroquiaModalComponent } from './dialogs/parroquia-modal/parroquia-modal.component';
import { ProvinciaModalComponent } from './dialogs/provincia-modal/provincia-modal.component';

/** Nivel geografico de un nodo del arbol. */
export type NivelGeografico = 'PROVINCIA' | 'CANTON' | 'PARROQUIA';

/**
 * Lo que la pantalla sabe de un nodo del arbol.
 *
 * <p>El nodo de PrimeNG (`TreeNode`) es la maquinaria de dibujo; esto es el dato de negocio que
 * viaja con el: el id real de la entidad, su nivel y el texto que va al lado del nombre.
 */
export interface NodoGeografia {
  id: number;
  nivel: NivelGeografico;
  nombre: string;
  /** Texto secundario del nodo: "2 parroquias", "320 electores"... */
  meta: string;
  /**
   * Id del padre en la jerarquia (la provincia de un canton o el canton de una parroquia).
   *
   * <p>El arbol del backend no lo repite en cada nodo, pero el frontend lo necesita
   * para editar: el PUT de una parroquia exige el canton al que pertenece.
   */
  padreId?: number;
  /** Entidad de origen, para el modo edicion del modal. */
  datos: ProvinciaNodoDto | CantonNodoDto | ParroquiaNodoDto;
}

/**
 * Configuracion geografica jerarquica (SDD 2026-11-16-config-geografica).
 *
 * <p>Una sola pantalla para tres niveles: Provincia → Canton → Parroquia. El arbol
 * se lee entero de una vez y el CRUD es contextual: cada nodo ofrece crear su nivel hijo, y el
 * menu contextual ofrece editar o eliminar.
 *
 * <p>El borrado en cascada no existe: el backend rechaza con 409 cuando la entidad todavia tiene
 * hijos, y el mensaje dice cuantos. Por eso el menu de eliminar avisa antes de intentarlo.
 */
@Component({
  selector: 'app-configuracion-geografica',
  standalone: true,
  imports: [
    CommonModule,
    TreeModule,
    ContextMenuModule,
    PrimeTemplate,
    ButtonComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    LoadingStateComponent,
  ],
  providers: [DialogService],
  templateUrl: './configuracion-geografica.page.html',
  styleUrls: ['./configuracion-geografica.page.scss'],
})
export class ConfiguracionGeograficaPage implements OnInit {
  private readonly api = inject(GeografiaApi);
  private readonly dialogService = inject(DialogService);
  private readonly toast = inject(ToastService);

  /** Estado de la carga del arbol. */
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  /** Arbol crudo que devuelve el backend. */
  private readonly arbol = signal<ProvinciaNodoDto[]>([]);

  /** Nodo sobre el que se abrio el menu contextual. */
  readonly nodoContexto = signal<TreeNode | null>(null);

  /**
   * Claves de los nodos expandidos.
   *
   * <p>Es un signal y no un `Set` a proposito: el arbol se vuelve a construir entero cada vez
   * que cambia la expansion. Mutar `node.expanded` en el sitio no refresca los hijos (los nodos
   * de PrimeNG son OnPush y reciben el MISMO objeto), asi que el estado vive aqui y los nodos
   * se vuelven a crear.
   */
  private readonly expandidos = signal<ReadonlySet<string>>(new Set<string>());

  /** Arbol que se dibuja. */
  readonly nodos = computed<TreeNode[]>(() =>
    this.construirNodos(this.arbol(), this.expandidos()),
  );

  /** Acciones del menu contextual, segun el nivel del nodo. */
  readonly menuItems = computed<MenuItem[]>(() => {
    const nodo = this.nodoContexto();
    const datos = nodo?.data as NodoGeografia | undefined;
    if (!datos) {
      return [];
    }

    const items: MenuItem[] = [
      {
        label: 'Editar',
        icon: 'pi pi-pencil',
        command: () => this.editar(datos),
      },
    ];

    items.push({
      label: 'Eliminar',
      icon: 'pi pi-trash',
      styleClass: 'text-red-500',
      command: () => this.eliminar(datos),
    });

    return items;
  });

  ngOnInit(): void {
    this.cargarArbol();
  }

  /** Carga (o recarga) el arbol completo desde el backend. */
  cargarArbol(): void {
    this.loading.set(true);
    this.error.set(null);

    this.api.obtenerArbol().subscribe({
      next: (arbol) => {
        this.arbol.set(arbol.provincias);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(
          err?.error?.message ??
            'No se pudo cargar la configuracion geografica',
        );
        this.loading.set(false);
      },
    });
  }

  /** Expande todos los nodos que tienen hijos. */
  expandirTodo(): void {
    this.expandidos.set(new Set(this.clavesConHijos(this.arbol())));
  }

  /** Colapsa todos los nodos. */
  colapsarTodo(): void {
    this.expandidos.set(new Set<string>());
  }

  /** El operador expandio un nodo: se recuerda para la proxima recarga. */
  onNodeExpand(event: { node: TreeNode }): void {
    if (event.node?.key) {
      this.expandirNodo(String(event.node.key));
    }
  }

  /** Expande un nodo concreto (por ejemplo, el padre de lo recien creado). */
  private expandirNodo(clave: string): void {
    this.expandidos.update((actuales) => new Set(actuales).add(clave));
  }

  /** El operador colapso un nodo. */
  onNodeCollapse(event: { node: TreeNode }): void {
    if (event.node?.key) {
      const clave = String(event.node.key);
      this.expandidos.update((actuales) => {
        const copia = new Set(actuales);
        copia.delete(clave);
        return copia;
      });
    }
  }

  /** Nodo del menu contextual: el arbol lo emite con el boton derecho. */
  onNodeContextMenuSelect(event: { node: TreeNode }): void {
    this.nodoContexto.set(event.node);
  }

  // ---------------------------------------------------------------------------
  // Alta
  // ---------------------------------------------------------------------------

  /** Abre el modal de alta de provincia. */
  crearProvincia(): void {
    this.abrirDialogo(
      ProvinciaModalComponent,
      'Nueva provincia',
      { mode: 'create' },
      'Provincia creada',
    );
  }

  /** Abre el modal de alta de canton bajo la provincia del nodo. */
  crearCanton(nodo: NodoGeografia): void {
    this.abrirDialogo(
      CantonModalComponent,
      `Nuevo canton en ${nodo.nombre}`,
      { mode: 'create', provinciaId: nodo.id, provinciaNombre: nodo.nombre },
      'Canton creado',
      this.clave('PROVINCIA', nodo.id),
    );
  }

  /** Abre el modal de alta de parroquia bajo el canton del nodo. */
  crearParroquia(nodo: NodoGeografia): void {
    this.abrirDialogo(
      ParroquiaModalComponent,
      `Nueva parroquia en ${nodo.nombre}`,
      { mode: 'create', cantonId: nodo.id, cantonNombre: nodo.nombre },
      'Parroquia creada',
      this.clave('CANTON', nodo.id),
    );
  }

  // ---------------------------------------------------------------------------
  // Edicion y baja
  // ---------------------------------------------------------------------------

  /** Abre el modal de edicion del nodo, segun su nivel. */
  editar(datos: NodoGeografia): void {
    switch (datos.nivel) {
      case 'PROVINCIA':
        this.abrirDialogo(
          ProvinciaModalComponent,
          'Editar provincia',
          { mode: 'edit', provincia: datos.datos as ProvinciaNodoDto },
          'Provincia actualizada',
        );
        break;
      case 'CANTON':
        this.abrirDialogo(
          CantonModalComponent,
          'Editar canton',
          {
            mode: 'edit',
            canton: datos.datos as CantonNodoDto,
            provinciaId: datos.padreId,
          },
          'Canton actualizado',
        );
        break;
      case 'PARROQUIA':
        this.abrirDialogo(
          ParroquiaModalComponent,
          'Editar parroquia',
          {
            mode: 'edit',
            parroquia: datos.datos as ParroquiaNodoDto,
            cantonId: datos.padreId,
          },
          'Parroquia actualizada',
        );
        break;
    }
  }

  /** Pide confirmacion y borra la entidad. El 409 (tiene hijos) se avisa con su motivo. */
  eliminar(datos: NodoGeografia): void {
    const ref = this.dialogService.open(ConfirmDialogComponent, {
      header: 'Eliminar',
      data: {
        message: `¿Estas seguro de que deseas eliminar "${datos.nombre}"?`,
      },
      width: '28rem',
    });

    // `open` devuelve null si el dialogo no se pudo crear: sin ref no hay nada que esperar.
    ref?.onClose.subscribe((confirmado: boolean) => {
      if (!confirmado) {
        return;
      }
      this.borrar(datos);
    });
  }

  /** Llama al endpoint de baja que corresponde al nivel del nodo. */
  private borrar(datos: NodoGeografia): void {
    const peticion = this.peticionDeBorrado(datos);

    peticion.subscribe({
      next: () => {
        this.toast.success('Eliminado', datos.nombre);
        this.cargarArbol();
      },
      error: (err) => {
        this.toast.error(
          'No se pudo eliminar',
          err?.error?.message ??
            'La entidad todavia tiene hijos o esta en uso.',
        );
      },
    });
  }

  /** Peticion de baja segun el nivel de la entidad. */
  private peticionDeBorrado(datos: NodoGeografia) {
    switch (datos.nivel) {
      case 'PROVINCIA':
        return this.api.eliminarProvincia(datos.id);
      case 'CANTON':
        return this.api.eliminarCanton(datos.id);
      case 'PARROQUIA':
        return this.api.eliminarParroquia(datos.id);
    }
  }

  /** Abre un modal y recarga el arbol cuando el modal avisa que guardo. */
  private abrirDialogo<T>(
    componente: Type<T>,
    header: string,
    data: Record<string, unknown>,
    mensajeExito: string,
    clavePadre?: string,
  ): void {
    const ref = this.dialogService.open(componente, {
      header,
      data,
      width: '32rem',
    });

    ref?.onClose.subscribe((resultado: unknown) => {
      const exito = (resultado as { success?: boolean } | undefined)?.success;
      if (exito) {
        this.toast.success(mensajeExito);
        // Se expande el padre: si no, el nodo nuevo queda debajo de un nodo cerrado y el
        // operador no ve lo que acaba de crear.
        if (clavePadre) {
          this.expandirNodo(clavePadre);
        }
        this.cargarArbol();
      }
    });
  }

  // ---------------------------------------------------------------------------
  // Construccion del arbol
  // ---------------------------------------------------------------------------

  /** Convierte la respuesta del backend en nodos de PrimeNG. */
  private construirNodos(
    provincias: ProvinciaNodoDto[],
    expandidos: ReadonlySet<string>,
  ): TreeNode[] {
    return provincias.map((provincia) => {
      const cantones = provincia.cantones ?? [];
      const clave = this.clave('PROVINCIA', provincia.id);

      const nodo: TreeNode = {
        key: clave,
        label: provincia.nombre,
        data: {
          id: provincia.id,
          nivel: 'PROVINCIA',
          nombre: provincia.nombre,
          meta: this.resumen(cantones.length, 'canton', 'cantones'),
          datos: provincia,
        } satisfies NodoGeografia,
        expanded: expandidos.has(clave),
      };

      nodo.children = cantones.map((canton) => {
        const parroquias = canton.parroquias ?? [];
        const claveCanton = this.clave('CANTON', canton.id);

        const nodoCanton: TreeNode = {
          key: claveCanton,
          label: canton.nombre,
          data: {
            id: canton.id,
            nivel: 'CANTON',
            nombre: canton.nombre,
            meta: this.resumen(parroquias.length, 'parroquia', 'parroquias'),
            padreId: provincia.id,
            datos: canton,
          } satisfies NodoGeografia,
          expanded: expandidos.has(claveCanton),
        };

        nodoCanton.children = parroquias.map((parroquia) => {
          const claveParroquia = this.clave('PARROQUIA', parroquia.id);

          const nodoParroquia: TreeNode = {
            key: claveParroquia,
            label: parroquia.nombre,
            data: {
              id: parroquia.id,
              nivel: 'PARROQUIA',
              nombre: parroquia.nombre,
              meta: parroquia.esUrbana ? 'urbana' : 'rural',
              padreId: canton.id,
              datos: parroquia,
            } satisfies NodoGeografia,
            expanded: expandidos.has(claveParroquia),
          };

          return nodoParroquia;
        });

        return nodoCanton;
      });

      return nodo;
    });
  }

  /** Clave estable de un nodo: nivel + id. */
  private clave(nivel: NivelGeografico, id: number): string {
    return `${nivel}-${id}`;
  }

  /** "1 canton" / "2 parroquias": el plural no se queda colgando. */
  private resumen(cantidad: number, singular: string, plural: string): string {
    return `${cantidad} ${cantidad === 1 ? singular : plural}`;
  }

  /**
   * Claves de todos los nodos que tienen hijos.
   *
   * <p>Se recorre el arbol CRUDO (el que devuelve el backend), no los nodos de PrimeNG: es el
   * unico que se puede recorrer sin depender de lo que ya esta dibujado.
   */
  private clavesConHijos(provincias: ProvinciaNodoDto[]): string[] {
    const claves: string[] = [];

    for (const provincia of provincias) {
      const cantones = provincia.cantones ?? [];
      if (cantones.length) {
        claves.push(this.clave('PROVINCIA', provincia.id));
      }
      for (const canton of cantones) {
        const parroquias = canton.parroquias ?? [];
        if (parroquias.length) {
          claves.push(this.clave('CANTON', canton.id));
        }
      }
    }

    return claves;
  }
}
