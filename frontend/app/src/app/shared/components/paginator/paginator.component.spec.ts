import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { PaginatorComponent } from './paginator.component';

describe('PaginatorComponent', () => {
  let component: PaginatorComponent;
  let fixture: ComponentFixture<PaginatorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaginatorComponent],
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

  it('should emit previous page unless already on the first one', () => {
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));

    component.page = 2;
    component.size = 20;
    component.totalElements = 100;
    component.previous();
    expect(emitted).toEqual([1]);

    component.page = 0;
    component.previous();
    expect(emitted).toEqual([1]);
  });

  it('should emit next page unless already on the last one', () => {
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));

    component.page = 0;
    component.size = 20;
    component.totalElements = 100;
    component.next();
    expect(emitted).toEqual([1]);

    component.page = 4;
    component.next();
    expect(emitted).toEqual([1]);
  });

  it('should emit the selected page size', () => {
    const emitted: number[] = [];
    component.sizeChange.subscribe((s) => emitted.push(s));

    const select = document.createElement('select');
    const option = document.createElement('option');
    option.value = '50';
    select.appendChild(option);
    select.value = '50';
    component.changeSize({ target: select } as unknown as Event);
    expect(emitted).toEqual([50]);
  });

  it('should render the page info text', () => {
    component.page = 1;
    component.size = 20;
    component.totalElements = 42;
    fixture.detectChanges();

    const info = fixture.debugElement.query(By.css('.paginator-info'));
    expect(info.nativeElement.textContent).toContain('Página 2 de 3');
    expect(info.nativeElement.textContent).toContain('42 total');
  });

  it('should hide the paginator when there is a single page', () => {
    component.size = 20;
    component.totalElements = 5;
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.paginator'))).toBeNull();
  });
});
