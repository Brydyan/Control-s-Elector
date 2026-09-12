import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { EmptyStateComponent } from './empty-state.component';

describe('EmptyStateComponent', () => {
  let component: EmptyStateComponent;
  let fixture: ComponentFixture<EmptyStateComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmptyStateComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(EmptyStateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render default title', () => {
    const titleEl = fixture.nativeElement.querySelector('.empty-state__title');
    expect(titleEl.textContent).toContain('No hay datos');
  });

  it('should render custom title and description', () => {
    component.title = 'Sin registros';
    component.description = 'No se encontraron actas pendientes';
    fixture.detectChanges();

    const titleEl = fixture.nativeElement.querySelector('.empty-state__title');
    const descEl = fixture.nativeElement.querySelector(
      '.empty-state__description',
    );

    expect(titleEl.textContent).toContain('Sin registros');
    expect(descEl.textContent).toContain('No se encontraron actas pendientes');
  });
});
