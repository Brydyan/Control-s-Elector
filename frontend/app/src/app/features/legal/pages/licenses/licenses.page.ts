import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

/**
 * Página de Open Source Licenses (T-COMPLIANCE-03).
 *
 * Muestra:
 * 1. Lista de dependencias npm con licencias (generada en build-time)
 * 2. Atribuciones de assets (iconos, imágenes, etc.) con licencias
 *
 * La página es pública a propósito: cualquier usuario debe poder
 * consultar qué licencias se usan en la aplicación.
 */
@Component({
  selector: 'app-licenses',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './licenses.page.html',
  styleUrl: './licenses.page.scss',
})
export class LicensesPage implements OnInit {
  private readonly http = inject(HttpClient);

  /** Lista de dependencias npm con licencias. */
  libraries = signal<License[]>([]);

  /** Atribuciones de assets (iconos, imágenes, fuentes, etc.). */
  assets = signal<Asset[]>([]);

  /** Estado de carga. */
  loading = signal(true);
  errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadLicenses();
    this.loadAssets();
  }

  /** Carga las licencias desde el archivo JSON generado en build-time. */
  private loadLicenses(): void {
    this.http.get<License[]>('/assets/legal/licenses.json').subscribe({
      next: (data) => {
        this.libraries.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Error loading licenses:', err);
        this.errorMessage.set('No se pudieron cargar las licencias npm.');
        this.loading.set(false);
      },
    });
  }

  /** Carga las atribuciones de assets desde el archivo JSON. */
  private loadAssets(): void {
    this.http.get<Asset[]>('/assets/legal/assets-attribution.json').subscribe({
      next: (data) => {
        this.assets.set(data);
      },
      error: (err) => {
        console.error('Error loading assets attribution:', err);
        // No es crítico si no se cargan los assets, continúa
      },
    });
  }

  /**
   * Determina si una licencia es restrictiva (GPL/AGPL).
   * Se usa para marcar visualmente licencias con requisitos especiales.
   */
  isRestrictiveLicense(license: string): boolean {
    if (!license) return false;
    const restrictive = ['GPL', 'AGPL'];
    return restrictive.some((l) => license.toUpperCase().includes(l));
  }
}

/** Interfaz para una dependencia npm con licencia. */
export interface License {
  name: string;
  version: string;
  license: string;
  repository?: string;
  restrictive?: boolean;
}

/** Interfaz para una atribución de asset. */
export interface Asset {
  id: string;
  name: string;
  author: string;
  license: string;
  license_url: string;
}
