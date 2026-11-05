import { ComponentFixture, TestBed } from '@angular/core/testing';
import { providePrimeNG } from 'primeng/config';
import { BadgeComponent } from './badge.component';

describe('BadgeComponent', () => {
  let component: BadgeComponent;
  let fixture: ComponentFixture<BadgeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BadgeComponent],
      providers: [providePrimeNG()],
    }).compileComponents();

    fixture = TestBed.createComponent(BadgeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should map the electoral states to the PrimeNG severities', () => {
    const cases: Array<[BadgeComponent['variant'], string]> = [
      ['APROBADA', 'p-tag-success'],
      ['ENVIADA', 'p-tag-info'],
      ['EN_DISPUTA', 'p-tag-warn'],
      ['ANULADA', 'p-tag-danger'],
      ['BORRADOR', 'p-tag-secondary'],
    ];

    for (const [variant, expected] of cases) {
      fixture.componentRef.setInput('variant', variant);
      fixture.detectChanges();

      const tag = fixture.nativeElement.querySelector('p-tag');
      expect(tag.classList).toContain(expected);
    }
  });

  it('should keep the institutional purple for the CORREGIDA state', () => {
    fixture.componentRef.setInput('variant', 'CORREGIDA');
    fixture.detectChanges();

    const tag = fixture.nativeElement.querySelector('p-tag');
    expect(tag.classList).toContain('badge-CORREGIDA');
  });
});
