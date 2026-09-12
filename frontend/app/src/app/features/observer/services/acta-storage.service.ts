import { Injectable } from '@angular/core';

export interface BorradorActaData {
  jrvId: number;
  cargoId: number;
  totalSufragantes: number;
  votosBlancos: number;
  votosNulos: number;
  votosPorCandidato: Record<number, number>;
  updatedAt: string;
}

@Injectable({
  providedIn: 'root',
})
export class ActaStorageService {
  private readonly PREFIX = 'electoral_borrador_';

  private getKey(jrvId: number, cargoId: number): string {
    return `${this.PREFIX}${jrvId}_${cargoId}`;
  }

  guardarBorrador(data: BorradorActaData): void {
    if (typeof localStorage === 'undefined') return;
    try {
      const payload = {
        ...data,
        updatedAt: new Date().toISOString(),
      };
      localStorage.setItem(
        this.getKey(data.jrvId, data.cargoId),
        JSON.stringify(payload),
      );
    } catch (err) {
      console.warn('No se pudo persistir borrador en localStorage', err);
    }
  }

  recuperarBorrador(jrvId: number, cargoId: number): BorradorActaData | null {
    if (typeof localStorage === 'undefined') return null;
    try {
      const raw = localStorage.getItem(this.getKey(jrvId, cargoId));
      if (!raw) return null;
      return JSON.parse(raw) as BorradorActaData;
    } catch {
      return null;
    }
  }

  eliminarBorrador(jrvId: number, cargoId: number): void {
    if (typeof localStorage === 'undefined') return;
    try {
      localStorage.removeItem(this.getKey(jrvId, cargoId));
    } catch (err) {
      console.warn('Error al eliminar borrador', err);
    }
  }
}
