import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { PAGE_DEFAULTS } from '../../models/page.model';
import { ButtonComponent } from '../../ui';

@Component({
  selector: 'app-paginator',
  standalone: true,
  imports: [CommonModule, ButtonComponent],
  templateUrl: './paginator.component.html',
})
export class PaginatorComponent {
  @Input() page: number = PAGE_DEFAULTS.PAGE;
  @Input() size: number = PAGE_DEFAULTS.SIZE;
  @Input() totalElements: number = 0;
  @Input() pageSizes: number[] = [10, 20, 50];

  @Output() pageChange = new EventEmitter<number>();
  @Output() sizeChange = new EventEmitter<number>();

  get totalPages(): number {
    if (this.size <= 0) {
      return 0;
    }
    return Math.ceil(this.totalElements / this.size);
  }

  previous(): void {
    if (this.page <= 0) {
      return;
    }
    this.pageChange.emit(this.page - 1);
  }

  next(): void {
    if (this.page + 1 >= this.totalPages) {
      return;
    }
    this.pageChange.emit(this.page + 1);
  }

  changeSize(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.sizeChange.emit(Number(select.value) || PAGE_DEFAULTS.SIZE);
  }
}
