import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MenuNode, MenuResponse } from './menu.model';

@Injectable({
  providedIn: 'root',
})
export class MenuService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/menus`;

  private readonly _menus = signal<MenuNode[]>([]);
  private readonly _loading = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);

  readonly menus = this._menus.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  loadMenus(): void {
    this._loading.set(true);
    this._error.set(null);

    this.getMyMenus().subscribe({
      next: (nodes) => {
        this._menus.set(nodes);
        this._loading.set(false);
      },
      error: (err) => {
        this._error.set(err?.message || 'Error cargando menús');
        this._loading.set(false);
      },
    });
  }

  getMyMenus(): Observable<MenuNode[]> {
    return this.http
      .get<MenuResponse>(`${this.baseUrl}/me`, { withCredentials: true })
      .pipe(
        map((res) => res.menus || []),
        tap((nodes) => this._menus.set(nodes)),
      );
  }
}
