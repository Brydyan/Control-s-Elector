import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MenuNode, MenuResponse } from './menu.model';

@Injectable({
  providedIn: 'root',
})
export class MenuService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/menus`;

  getMyMenus(): Observable<MenuNode[]> {
    return this.http
      .get<MenuResponse>(`${this.baseUrl}/me`, { withCredentials: true })
      .pipe(map((res) => res.menus || []));
  }
}
