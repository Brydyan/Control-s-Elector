import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { providePrimeNG } from 'primeng/config';
import { PaginatorComponent } from './paginator.component';

describe('PaginatorComponent', () => {
  let component: PaginatorComponent;
  let fixture: ComponentFixture<PaginatorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaginatorComponent],
      providers: [providePrimeNG()],
    }).compileComponents();

    fixture = TestBed.createComponent(PaginatorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should compute total pages with ceil', () => {
    component.size = 20;
    component.totalElements = 42;
    expect(component.totalPages).toBe(3);
  });

  it('should emit the page reported by the paginator', () => {
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));

    component.size = 20;
    component.onPageChange({ first: 20, rows: 20 });

    expect(emitted).toEqual([1]);
  });

  it('should translate the record index into the first record of the page', () => {
    component.page = 1;
    component.size = 20;

    expect(component.firstRecordIndex).toBe(20);
  });

  it('should emit the page size when the paginator changes it', () => {
    const emitted: number[] = [];
    component.sizeChange.subscribe((s) => emitted.push(s));

    component.size = 20;
    component.onPageChange({ first: 0, rows: 50 });

    expect(emitted).toEqual([50]);
  });

  it('should not emit a size change when the size did not change', () => {
    const emitted: number[] = [];
    component.sizeChange.subscribe((s) => emitted.push(s));

    component.size = 20;
    component.onPageChange({ first: 1, rows: 20 });

    expect(emitted).toEqual([]);
  });

  it('should render the page info text', () => {
    fixture.componentRef.setInput('page', 1);
    fixture.componentRef.setInput('size', 20);
    fixture.componentRef.setInput('totalElements', 42);
    fixture.detectChanges();

    const info = fixture.debugElement.query(By.css('.p-paginator-current'));
    expect(info).toBeTruthy();
    expect(info.nativeElement.textContent).toContain('Página 2 de 3');
  });

  it('should hide the paginator when there is a single page', () => {
    component.size = 20;
    component.totalElements = 5;
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('p-paginator'))).toBeNull();
  });
});
