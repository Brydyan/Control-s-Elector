import { Injectable } from '@angular/core';
import { CatalogoApi } from '../data/catalogo.api';

@Injectable({ providedIn: 'root' })
export class CatalogoService extends CatalogoApi {}
