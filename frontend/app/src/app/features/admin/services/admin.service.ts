import { Injectable } from '@angular/core';
import { AdminApi } from '../data/admin.api';

@Injectable({ providedIn: 'root' })
export class AdminService extends AdminApi {}
